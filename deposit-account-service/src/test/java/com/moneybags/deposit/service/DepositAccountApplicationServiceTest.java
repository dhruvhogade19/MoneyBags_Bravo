package com.moneybags.deposit.service;

import com.moneybags.deposit.domain.DomainTypes.AccountStatus;
import com.moneybags.deposit.domain.DomainTypes.OperatingInstruction;
import com.moneybags.deposit.dto.AccountRequests.OpenDepositAccountRequest;
import com.moneybags.deposit.dto.AccountRequests.StatusCommand;
import com.moneybags.deposit.dto.AccountResponses.AccountDetailView;
import com.moneybags.deposit.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class DepositAccountApplicationServiceTest {
    @Autowired
    private DepositAccountApplicationService service;

    @Test
    void openingIsIdempotentAndCreatesPendingAccount() {
        OpenDepositAccountRequest request = request();
        AccountDetailView first = service.open(request, "open-key-1", "tester", "corr-1");
        AccountDetailView replay = service.open(request, "open-key-1", "tester", "corr-2");

        assertThat(first.accountId()).isEqualTo(replay.accountId());
        assertThat(first.status()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(first.maskedAccountNumber()).endsWith(first.maskedAccountNumber().substring(first.maskedAccountNumber().length() - 4));
        assertThat(first.holders()).hasSize(1);
    }

    @Test
    void lifecycleRejectsInvalidTransitionAndSupportsActivation() {
        AccountDetailView opened = service.open(request(), "open-key-2", "tester", "corr-3");
        AccountDetailView active = service.command(opened.accountId(), "activate",
                new StatusCommand("OPENING_APPROVED", null, null), null, "checker", "corr-4");

        assertThat(active.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThatThrownBy(() -> service.command(opened.accountId(), "activate",
                new StatusCommand("AGAIN", null, null), null, "checker", "corr-5"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void reusedIdempotencyKeyWithDifferentBodyIsRejected() {
        service.open(request(), "open-key-3", "tester", "corr-6");
        OpenDepositAccountRequest changed = new OpenDepositAccountRequest(List.of("CIF-100"), "CIF-100",
                "prod-current", 1L, "INR", BigDecimal.ZERO, "BR-001", OperatingInstruction.SINGLE,
                List.of(), "BRANCH", "ext-2");

        assertThatThrownBy(() -> service.open(changed, "open-key-3", "tester", "corr-7"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different request");
    }

    private OpenDepositAccountRequest request() {
        return new OpenDepositAccountRequest(List.of("CIF-100"), "CIF-100", "prod-savings", 1L,
                "INR", BigDecimal.ZERO, "BR-001", OperatingInstruction.SINGLE, List.of(), "BRANCH", "ext-1");
    }
}
