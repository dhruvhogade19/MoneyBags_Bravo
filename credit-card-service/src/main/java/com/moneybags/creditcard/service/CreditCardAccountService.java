package com.moneybags.creditcard.service;

import com.moneybags.creditcard.domain.CreditCardTypes.AccountStatus;
import com.moneybags.creditcard.entity.CreditCardAccount;
import com.moneybags.creditcard.entity.CreditCardApplication;
import com.moneybags.creditcard.exception.ApiException;
import com.moneybags.creditcard.integration.AccountingLifecycleGateway;
import com.moneybags.creditcard.integration.NotificationGateway;
import com.moneybags.creditcard.repository.CreditCardAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.security.SecureRandom;
import java.util.Map;

@Service
public class CreditCardAccountService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger LOG = LoggerFactory.getLogger(CreditCardAccountService.class);
    private final CreditCardAccountRepository accounts;
    private final AccountingLifecycleGateway accounting;
    private final NotificationGateway notifications;

    public CreditCardAccountService(CreditCardAccountRepository accounts, AccountingLifecycleGateway accounting,
                                    NotificationGateway notifications) {
        this.accounts = accounts;
        this.accounting = accounting;
        this.notifications = notifications;
    }

    public CreditCardAccount createForApplication(CreditCardApplication application) {
        if (application.approvedCreditLimit == null) {
            throw new ApiException(HttpStatus.CONFLICT, "An approved credit limit is required to create an account");
        }
        if (accounts.existsByApplicationId(application.id)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account already exists for this application");
        }

        var account = new CreditCardAccount();
        account.applicationId = application.id;
        account.cifId = application.cifId;
        account.productCode = application.productCode;
        account.age = application.age;
        account.salary = application.salary;
        account.cardNumber = generateCardNumber();
        account.sanctionedLimit = application.approvedCreditLimit;
        account.purchaseInterestRateSnapshot = application.purchaseInterestRateSnapshot;
        account.availableLimit = application.approvedCreditLimit;
        account.outstandingAmount = BigDecimal.ZERO;
        account.status = AccountStatus.BLOCKED;
        account.openedAt = OffsetDateTime.now(ZoneOffset.UTC);
        var savedAccount = accounts.save(account);
        String accountReference = accountReference(savedAccount.id);
        var event = new AccountingLifecycleGateway.AccountOpenedEvent(
                "CARD-OPEN:" + accountReference, "CREDIT_CARD_ACCOUNT_OPENED", "CREDIT_CARD_ACCOUNT",
                accountReference, savedAccount.productCode, "INR", LocalDate.now(), savedAccount.openedAt);
        var response = accounting.publishOpening(event);
        if (response != null && "OPEN".equals(response.accountingLifecycleState())) {
            savedAccount.status = AccountStatus.ACTIVE;
            sendAccountCreatedNotification(savedAccount, accountReference);
        }
        return savedAccount;
    }

    private String generateCardNumber() {
        StringBuilder cardNumber = new StringBuilder("4000");
        for (int index = 0; index < 12; index++) {
            cardNumber.append(RANDOM.nextInt(10));
        }
        return cardNumber.toString();
    }

    private String accountReference(Long accountId) {
        return "CC-" + accountId;
    }

    private void sendAccountCreatedNotification(CreditCardAccount account, String accountReference) {
        try {
            String cardLastFour = account.cardNumber.substring(account.cardNumber.length() - 4);
            notifications.sendAccountCreated(new NotificationGateway.AccountCreatedNotification(
                    account.cifId, "CREDIT_CARD_CREATED", accountReference,
                    Map.of("accountId", accountReference, "cardLastFour", cardLastFour)));
        } catch (RestClientException exception) {
            LOG.warn("Credit-card account {} was opened but its creation notification could not be delivered", account.id,
                    exception);
        }
    }
}
