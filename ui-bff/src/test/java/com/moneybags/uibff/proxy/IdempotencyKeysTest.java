package com.moneybags.uibff.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneybags.uibff.api.BffApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class IdempotencyKeysTest {
    @Test
    void preservesAValidCallerKey() {
        assertThat(IdempotencyKeys.preserveOrGenerate(HttpMethod.POST, "payment:browser-123"))
                .isEqualTo("payment:browser-123");
    }

    @Test
    void generatesKeysForMutationsOnly() {
        assertThat(UUID.fromString(IdempotencyKeys.preserveOrGenerate(HttpMethod.PATCH, null))).isNotNull();
        assertThat(IdempotencyKeys.preserveOrGenerate(HttpMethod.GET, null)).isNull();
    }

    @Test
    void rejectsUnsafeKeys() {
        assertThatThrownBy(() -> IdempotencyKeys.preserveOrGenerate(HttpMethod.POST, "bad key\r\nvalue"))
                .isInstanceOf(BffApiException.class);
    }
}
