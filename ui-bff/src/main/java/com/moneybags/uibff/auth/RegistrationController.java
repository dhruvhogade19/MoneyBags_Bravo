package com.moneybags.uibff.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {
    private final IdentityRegistrationClient registrations;

    public RegistrationController(IdentityRegistrationClient registrations) {
        this.registrations = registrations;
    }

    @PostMapping({"/api/registration", "/api/auth/register"})
    public ResponseEntity<byte[]> register(HttpServletRequest request) throws IOException {
        var response = registrations.register(request.getContentType(), request.getInputStream().readAllBytes());
        return ResponseEntity.status(response.status()).headers(response.headers()).body(response.body());
    }
}
