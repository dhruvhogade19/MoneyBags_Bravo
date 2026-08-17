package com.moneybags.uibff.proxy;

import com.moneybags.uibff.api.BffApiException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

final class IdempotencyKeys {
    private static final Set<HttpMethod> MUTATING_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,160}");

    private IdempotencyKeys() {
    }

    static String preserveOrGenerate(HttpMethod method, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            if (!SAFE_KEY.matcher(candidate).matches()) {
                throw new BffApiException(HttpStatus.BAD_REQUEST,
                        "Idempotency-Key must use 1-160 letters, digits, dots, underscores, colons or hyphens");
            }
            return candidate;
        }
        return MUTATING_METHODS.contains(method) ? UUID.randomUUID().toString() : null;
    }
}
