package com.moneybags.identity.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/users")
@PreAuthorize("hasRole('BANK_ADMIN')")
public class UserAdministrationController {
    private final BankUserRepository repository;
    private final PasswordEncoder encoder;

    public UserAdministrationController(BankUserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByUsernameIgnoreCase(username)) {
            return ResponseEntity.status(409).build();
        }
        String role = request.role().toUpperCase(Locale.ROOT);
        if (!role.equals("BANK_ADMIN") && !role.equals("CONSUMER")) {
            return ResponseEntity.badRequest().build();
        }
        BankUser saved = repository.save(new BankUser(username, encoder.encode(request.password()),
                request.customerId(), request.tenantId(), role));
        return ResponseEntity.created(URI.create("/api/v1/identity/users/" + saved.getId()))
                .body(UserResponse.from(saved));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable String id) {
        return repository.findById(id).map(UserResponse::from)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(min = 12, max = 128) String password,
            @Size(max = 64) String customerId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,64}") String tenantId,
            @NotBlank String role) {}

    public record UserResponse(String id, String username, String customerId, String tenantId,
                               String roles, boolean enabled, boolean accountNonLocked) {
        static UserResponse from(BankUser user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getCustomerId(), user.getTenantId(),
                    user.getRoles(), user.isEnabled(), user.isAccountNonLocked());
        }
    }
}
