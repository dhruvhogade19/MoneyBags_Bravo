package com.moneybags.identity.web;

import com.moneybags.identity.user.BankUser;
import com.moneybags.identity.user.BankUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
@RequestMapping("/api/v1/identity/registrations")
@ConditionalOnProperty(prefix = "moneybags.identity.registration", name = "enabled", havingValue = "true")
public class ConsumerRegistrationController {
    private final BankUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public ConsumerRegistrationController(BankUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    @Transactional
    ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(CONFLICT, "An account already exists for this email address");
        }

        BankUser saved = users.save(new BankUser(
                username, passwordEncoder.encode(request.password()), null, "moneybags", "CONSUMER"));
        RegistrationResponse response = new RegistrationResponse(saved.getId(), saved.getUsername(), "CONSUMER");
        return ResponseEntity.created(URI.create("/api/v1/identity/users/" + saved.getId())).body(response);
    }

    public record RegistrationRequest(
            @NotBlank @Email @Size(max = 100) String username,
            @NotBlank @Size(min = 12, max = 128) String password) {
    }

    public record RegistrationResponse(String userId, String username, String role) {
    }
}
