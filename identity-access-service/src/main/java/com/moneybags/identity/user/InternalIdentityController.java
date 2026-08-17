package com.moneybags.identity.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/identity/users")
@PreAuthorize("hasAuthority('SCOPE_identity:service')")
public class InternalIdentityController {
    private final BankUserRepository repository;

    public InternalIdentityController(BankUserRepository repository) {
        this.repository = repository;
    }

    @PutMapping("/{userId}/customer-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void linkCustomer(@PathVariable String userId, @Valid @RequestBody CustomerLinkRequest request) {
        BankUser user = repository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity user not found"));
        if (!user.getRoles().contains("CONSUMER")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a consumer identity can be linked to a CIF");
        }
        if (!user.getTenantId().equals(request.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Identity tenant does not match CIF tenant");
        }
        if (user.getCustomerId() != null && !user.getCustomerId().equals(request.customerId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity is already linked to another CIF");
        }
        user.setCustomerId(request.customerId());
        repository.save(user);
    }

    public record CustomerLinkRequest(
            @NotBlank String customerId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String tenantId) {}
}
