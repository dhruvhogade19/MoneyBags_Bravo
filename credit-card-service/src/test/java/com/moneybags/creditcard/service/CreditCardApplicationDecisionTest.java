package com.moneybags.creditcard.service;

import com.moneybags.creditcard.domain.CreditCardTypes.ApplicationStatus;
import com.moneybags.creditcard.domain.CreditCardTypes.EligibilityStatus;
import com.moneybags.creditcard.dto.CreditCardDtos.ApplicationRequest;
import com.moneybags.creditcard.entity.CreditCardApplication;
import com.moneybags.creditcard.integration.CreditCardReferenceGateway;
import com.moneybags.creditcard.integration.AccountingLifecycleGateway;
import com.moneybags.creditcard.repository.CreditCardAccountRepository;
import com.moneybags.creditcard.repository.CreditCardApplicationRepository;
import com.moneybags.creditcard.repository.CreditCardHoldRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreditCardApplicationDecisionTest {
    private final CreditCardApplicationRepository applications = mock(CreditCardApplicationRepository.class);
    private final CreditCardAccountRepository accounts = mock(CreditCardAccountRepository.class);
    private final CreditCardAccountService accountService = mock(CreditCardAccountService.class);
    private final CreditCardHoldRepository holds = mock(CreditCardHoldRepository.class);
    private final CreditCardReferenceGateway references = mock(CreditCardReferenceGateway.class);
    private final AccountingLifecycleGateway accounting = mock(AccountingLifecycleGateway.class);
    private final CreditCardService service = new CreditCardService(applications, accounts, accountService, holds, references, accounting);

    @Test
    void eligibleApplicationIsApprovedAndCreatesAnAccount() {
        stubCif();
        when(references.validateApplication(eq("VISA"), any(), any())).thenReturn(
                new CreditCardReferenceGateway.ProductValidation(true,
                        new CreditCardReferenceGateway.ApplicableInterestRule(new BigDecimal("42.0000"))));
        when(applications.save(any(CreditCardApplication.class))).thenAnswer(invocation -> {
            CreditCardApplication application = invocation.getArgument(0);
            application.id = 1001L;
            return application;
        });

        var response = service.submit(new ApplicationRequest(501L, "VISA", new BigDecimal("100000.00")));

        assertEquals(ApplicationStatus.APPROVED, response.applicationStatus());
        assertEquals(EligibilityStatus.ELIGIBLE, response.eligibilityStatus());
        assertEquals(new BigDecimal("100000.00"), response.approvedCreditLimit());
        assertEquals(new BigDecimal("42.0000"), response.purchaseInterestRateSnapshot());
        verify(accountService).createForApplication(any(CreditCardApplication.class));
    }

    @Test
    void ineligibleApplicationIsRejectedWithoutCreatingAnAccount() {
        stubCif();
        when(references.validateApplication(eq("VISA"), any(), any())).thenReturn(
                new CreditCardReferenceGateway.ProductValidation(false,
                        new CreditCardReferenceGateway.ApplicableInterestRule(new BigDecimal("42.0000"))));
        when(applications.save(any(CreditCardApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.submit(new ApplicationRequest(501L, "VISA", new BigDecimal("100000.00")));

        assertEquals(ApplicationStatus.REJECTED, response.applicationStatus());
        assertEquals(EligibilityStatus.NOT_ELIGIBLE, response.eligibilityStatus());
        assertEquals(null, response.approvedCreditLimit());
        verify(accountService, never()).createForApplication(any());
    }

    private void stubCif() {
        when(references.getCreditCardDetails(501L)).thenReturn(
                new CreditCardReferenceGateway.CifDetails(501L, "SALARIED", new BigDecimal("75000.00"), 30, "APPROVED"));
    }
}
