package com.moneybags.creditcard.service;

import com.moneybags.creditcard.domain.CreditCardTypes.AccountStatus;
import com.moneybags.creditcard.entity.CreditCardAccount;
import com.moneybags.creditcard.entity.CreditCardApplication;
import com.moneybags.creditcard.integration.AccountingLifecycleGateway;
import com.moneybags.creditcard.integration.NotificationGateway;
import com.moneybags.creditcard.repository.CreditCardAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditCardAccountOpeningTest {
    private final CreditCardAccountRepository accounts = mock(CreditCardAccountRepository.class);
    private final AccountingLifecycleGateway accounting = mock(AccountingLifecycleGateway.class);
    private final NotificationGateway notifications = mock(NotificationGateway.class);
    private final CreditCardAccountService service = new CreditCardAccountService(accounts, accounting, notifications);

    @Test
    void accountIsActivatedOnlyAfterAccountingConfirmsOpen() {
        CreditCardApplication application = approvedApplication();
        when(accounts.existsByApplicationId(1001L)).thenReturn(false);
        when(accounts.save(any(CreditCardAccount.class))).thenAnswer(invocation -> {
            CreditCardAccount account = invocation.getArgument(0);
            account.id = 5001L;
            return account;
        });
        when(accounting.publishOpening(any())).thenReturn(new AccountingLifecycleGateway.LifecycleResponse("OPEN"));

        CreditCardAccount account = service.createForApplication(application);

        assertEquals(AccountStatus.ACTIVE, account.status);
        var order = inOrder(accounting, notifications);
        order.verify(accounting).publishOpening(new AccountingLifecycleGateway.AccountOpenedEvent(
                "CARD-OPEN:CC-5001", "CREDIT_CARD_ACCOUNT_OPENED", "CREDIT_CARD_ACCOUNT", "CC-5001",
                "CARD-GOLD", "INR", account.openedAt.toLocalDate(), account.openedAt));
        order.verify(notifications).sendAccountCreated(new NotificationGateway.AccountCreatedNotification(
                501L, "CREDIT_CARD_CREATED", "CC-5001",
                java.util.Map.of("accountId", "CC-5001", "cardLastFour",
                        account.cardNumber.substring(account.cardNumber.length() - 4))));
    }

    @Test
    void accountRemainsBlockedWhenAccountingDoesNotConfirmOpen() {
        CreditCardApplication application = approvedApplication();
        when(accounts.existsByApplicationId(1001L)).thenReturn(false);
        when(accounts.save(any(CreditCardAccount.class))).thenAnswer(invocation -> {
            CreditCardAccount account = invocation.getArgument(0);
            account.id = 5001L;
            return account;
        });
        when(accounting.publishOpening(any())).thenReturn(new AccountingLifecycleGateway.LifecycleResponse("PENDING"));

        CreditCardAccount account = service.createForApplication(application);

        assertEquals(AccountStatus.BLOCKED, account.status);
        verify(notifications, never()).sendAccountCreated(any());
    }

    @Test
    void notificationFailureDoesNotUndoSuccessfulOpening() {
        CreditCardApplication application = approvedApplication();
        when(accounts.existsByApplicationId(1001L)).thenReturn(false);
        when(accounts.save(any(CreditCardAccount.class))).thenAnswer(invocation -> {
            CreditCardAccount account = invocation.getArgument(0);
            account.id = 5001L;
            return account;
        });
        when(accounting.publishOpening(any())).thenReturn(new AccountingLifecycleGateway.LifecycleResponse("OPEN"));
        doThrow(new RestClientException("Notification service unavailable"))
                .when(notifications).sendAccountCreated(any());

        CreditCardAccount account = service.createForApplication(application);

        assertEquals(AccountStatus.ACTIVE, account.status);
    }

    private CreditCardApplication approvedApplication() {
        CreditCardApplication application = new CreditCardApplication();
        application.id = 1001L;
        application.cifId = 501L;
        application.productCode = "CARD-GOLD";
        application.age = 30;
        application.salary = new BigDecimal("75000.00");
        application.approvedCreditLimit = new BigDecimal("100000.00");
        application.purchaseInterestRateSnapshot = new BigDecimal("42.0000");
        return application;
    }
}
