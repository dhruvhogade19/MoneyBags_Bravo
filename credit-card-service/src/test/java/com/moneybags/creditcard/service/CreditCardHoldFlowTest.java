package com.moneybags.creditcard.service;

import com.moneybags.creditcard.domain.CreditCardTypes.AccountStatus;
import com.moneybags.creditcard.domain.CreditCardTypes.HoldStatus;
import com.moneybags.creditcard.dto.CreditCardDtos.AmountRequest;
import com.moneybags.creditcard.dto.CreditCardDtos.HoldRequest;
import com.moneybags.creditcard.entity.CreditCardAccount;
import com.moneybags.creditcard.entity.CreditCardHold;
import com.moneybags.creditcard.exception.ApiException;
import com.moneybags.creditcard.integration.CreditCardReferenceGateway;
import com.moneybags.creditcard.integration.AccountingLifecycleGateway;
import com.moneybags.creditcard.repository.CreditCardAccountRepository;
import com.moneybags.creditcard.repository.CreditCardApplicationRepository;
import com.moneybags.creditcard.repository.CreditCardHoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardHoldFlowTest {
    @Mock private CreditCardApplicationRepository applications;
    @Mock private CreditCardAccountRepository accounts;
    @Mock private CreditCardAccountService accountService;
    @Mock private CreditCardHoldRepository holds;
    @Mock private CreditCardReferenceGateway references;
    @Mock private AccountingLifecycleGateway accounting;

    private CreditCardService service;
    private CreditCardAccount account;

    @BeforeEach
    void setUp() {
        service = new CreditCardService(applications, accounts, accountService, holds, references, accounting);
        account = new CreditCardAccount();
        account.id = 10L;
        account.status = AccountStatus.ACTIVE;
        account.availableLimit = new BigDecimal("100000.00");
        account.outstandingAmount = BigDecimal.ZERO;
        when(accounts.lockById(10L)).thenReturn(Optional.of(account));
    }

    @Test
    void createHoldReservesLimitOnceAndRetriesAreIdempotent() {
        when(holds.findByReferenceId("PAY-12345")).thenReturn(Optional.empty());
        when(holds.save(any(CreditCardHold.class))).thenAnswer(invocation -> {
            CreditCardHold hold = invocation.getArgument(0);
            hold.id = 99L;
            return hold;
        });

        var created = service.createHold(10L, new HoldRequest("PAY-12345", new BigDecimal("50000.00")));

        assertEquals(HoldStatus.HELD, created.status());
        assertEquals(new BigDecimal("50000.00"), account.availableLimit);
        ArgumentCaptor<CreditCardHold> holdCaptor = ArgumentCaptor.forClass(CreditCardHold.class);
        verify(holds).save(holdCaptor.capture());

        when(holds.findByReferenceId("PAY-12345")).thenReturn(Optional.of(holdCaptor.getValue()));
        var retried = service.createHold(10L, new HoldRequest("PAY-12345", new BigDecimal("50000.00")));

        assertEquals(99L, retried.holdId());
        assertEquals(new BigDecimal("50000.00"), account.availableLimit);
        verify(holds, times(1)).save(any(CreditCardHold.class));
    }

    @Test
    void captureChangesOutstandingOnlyOnce() {
        CreditCardHold hold = heldHold(99L, new BigDecimal("50000.00"));
        when(holds.findById(99L)).thenReturn(Optional.of(hold));

        service.captureHold(10L, 99L);
        service.captureHold(10L, 99L);

        assertEquals(HoldStatus.CAPTURED, hold.status);
        assertEquals(new BigDecimal("50000.00"), account.outstandingAmount);
        assertEquals(new BigDecimal("100000.00"), account.availableLimit);
    }

    @Test
    void releaseRestoresLimitOnlyOnceAndCapturedHoldCannotBeReleased() {
        CreditCardHold held = heldHold(99L, new BigDecimal("50000.00"));
        account.availableLimit = new BigDecimal("50000.00");
        when(holds.findById(99L)).thenReturn(Optional.of(held));

        service.releaseHold(10L, 99L);
        service.releaseHold(10L, 99L);

        assertEquals(HoldStatus.RELEASED, held.status);
        assertEquals(new BigDecimal("100000.00"), account.availableLimit);

        held.status = HoldStatus.CAPTURED;
        ApiException exception = assertThrows(ApiException.class, () -> service.releaseHold(10L, 99L));
        assertEquals(409, exception.status.value());
    }

    @Test
    void releasedHoldCannotBeCaptured() {
        CreditCardHold released = heldHold(99L, new BigDecimal("50000.00"));
        released.status = HoldStatus.RELEASED;
        when(holds.findById(99L)).thenReturn(Optional.of(released));

        ApiException exception = assertThrows(ApiException.class, () -> service.captureHold(10L, 99L));

        assertEquals(409, exception.status.value());
        assertEquals(BigDecimal.ZERO, account.outstandingAmount);
    }

    @Test
    void concurrentRequestsAreSerializedThroughPessimisticAccountLock() {
        when(holds.findByReferenceId(anyString())).thenReturn(Optional.empty());
        when(holds.save(any(CreditCardHold.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createHold(10L, new HoldRequest("PAY-A", new BigDecimal("80000.00")));
        ApiException exception = assertThrows(ApiException.class,
                () -> service.createHold(10L, new HoldRequest("PAY-B", new BigDecimal("70000.00"))));

        assertEquals(409, exception.status.value());
        assertEquals(new BigDecimal("20000.00"), account.availableLimit);
        verify(accounts, times(2)).lockById(10L);
    }

    @Test
    void billPaymentSmallerThanOutstandingIsAppliedInFull() {
        account.availableLimit = new BigDecimal("50000.00");
        account.outstandingAmount = new BigDecimal("50000.00");

        service.billPaid(10L, new AmountRequest(new BigDecimal("30000.00")));

        assertEquals(new BigDecimal("20000.00"), account.outstandingAmount);
        assertEquals(new BigDecimal("80000.00"), account.availableLimit);
    }

    @Test
    void billPaymentEqualToOutstandingClearsTheBalance() {
        account.availableLimit = new BigDecimal("50000.00");
        account.outstandingAmount = new BigDecimal("50000.00");

        service.billPaid(10L, new AmountRequest(new BigDecimal("50000.00")));

        assertEquals(new BigDecimal("0.00"), account.outstandingAmount);
        assertEquals(new BigDecimal("100000.00"), account.availableLimit);
    }

    @Test
    void billPaymentGreaterThanOutstandingAppliesOnlyOutstandingAmount() {
        account.availableLimit = new BigDecimal("50000.00");
        account.outstandingAmount = new BigDecimal("50000.00");

        service.billPaid(10L, new AmountRequest(new BigDecimal("60000.00")));

        assertEquals(new BigDecimal("0.00"), account.outstandingAmount);
        assertEquals(new BigDecimal("100000.00"), account.availableLimit);
    }

    @Test
    void zeroOrNegativeBillPaymentIsRejected() {
        for (BigDecimal amount : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-1.00")}) {
            ApiException exception = assertThrows(ApiException.class, () -> service.billPaid(10L, new AmountRequest(amount)));
            assertEquals(400, exception.status.value());
        }
    }

    @Test
    void closeLeavesAccountClosurePendingWhenAccountingHasBlockers() {
        when(accounting.clearance("CC-10")).thenReturn(
                new AccountingLifecycleGateway.ClearanceResponse(false, java.util.List.of("NON_ZERO_BALANCE")));

        var response = service.close(10L);

        assertEquals(AccountStatus.CLOSURE_PENDING, response.status());
        verify(accounting, never()).publishClosure(any());
    }

    @Test
    void closeMarksAccountClosedOnlyAfterAccountingConfirmsClosure() {
        when(accounting.clearance("CC-10")).thenReturn(
                new AccountingLifecycleGateway.ClearanceResponse(true, java.util.List.of()));
        when(accounting.publishClosure(any())).thenReturn(new AccountingLifecycleGateway.LifecycleResponse("CLOSED"));

        var response = service.close(10L);

        assertEquals(AccountStatus.CLOSED, response.status());
        verify(accounting).publishClosure(any());
    }

    private CreditCardHold heldHold(Long id, BigDecimal amount) {
        CreditCardHold hold = new CreditCardHold();
        hold.id = id;
        hold.accountId = 10L;
        hold.amount = amount;
        hold.status = HoldStatus.HELD;
        return hold;
    }
}
