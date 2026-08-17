package com.moneybags.identity.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/registrations")
public class ConsumerRegistrationController {
    private static final String DEFAULT_TENANT = "moneybags";

    private final BankUserRepository repository;
    private final PasswordEncoder encoder;

    public ConsumerRegistrationController(BankUserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        String username = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByUsernameIgnoreCase(username)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        BankUser saved = repository.save(new BankUser(
                username, encoder.encode(request.password()), null, DEFAULT_TENANT, "CONSUMER"));
        return ResponseEntity.created(URI.create("/api/v1/identity/users/" + saved.getId()))
                .body(new RegistrationResponse(saved.getId(), saved.getUsername(), "PENDING_PROFILE"));
    }

    public record RegistrationRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 12, max = 128) String password) {}

    public record RegistrationResponse(String userId, String username, String onboardingStatus) {}
}
