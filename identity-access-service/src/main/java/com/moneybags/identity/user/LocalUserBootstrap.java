package com.moneybags.identity.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalUserBootstrap implements ApplicationRunner {
    private final BankUserRepository repository;
    private final PasswordEncoder encoder;
    private final String adminPassword;
    private final String consumerPassword;

    public LocalUserBootstrap(BankUserRepository repository, PasswordEncoder encoder,
                              @Value("${moneybags.identity.bootstrap.admin-password}") String adminPassword,
                              @Value("${moneybags.identity.bootstrap.consumer-password}") String consumerPassword) {
        this.repository = repository;
        this.encoder = encoder;
        this.adminPassword = adminPassword;
        this.consumerPassword = consumerPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent("admin@moneybags.local", adminPassword, null, "BANK_ADMIN");
        // A new consumer is intentionally not linked to a customer yet. The CIF
        // onboarding flow creates the customer and links its generated ID.
        createIfAbsent("consumer@moneybags.local", consumerPassword, null, "CONSUMER");
    }

    private void createIfAbsent(String username, String password, String customerId, String role) {
        if (!repository.existsByUsernameIgnoreCase(username)) {
            repository.save(new BankUser(username, encoder.encode(password), customerId, "moneybags", role));
        }
    }
}
