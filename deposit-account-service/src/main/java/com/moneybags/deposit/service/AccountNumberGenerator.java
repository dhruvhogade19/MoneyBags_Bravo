package com.moneybags.deposit.service;

import com.moneybags.deposit.config.DepositAccountProperties;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.repository.DepositAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccountNumberGenerator {
    private final SecureRandom random = new SecureRandom();
    private final DepositAccountRepository repository;
    private final DepositAccountProperties properties;

    public AccountNumberGenerator(DepositAccountRepository repository, DepositAccountProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public String next() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder value = new StringBuilder(properties.accountNumberPrefix());
            for (int i = 0; i < properties.accountNumberRandomDigits(); i++) value.append(random.nextInt(10));
            String candidate = value.toString();
            if (!repository.existsByAccountNumber(candidate)) return candidate;
        }
        throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NUMBER_CONFLICT",
                "Could not allocate a unique account number");
    }
}

