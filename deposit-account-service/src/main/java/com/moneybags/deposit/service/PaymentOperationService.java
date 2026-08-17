package com.moneybags.deposit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.deposit.config.DepositAccountProperties;
import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.dto.PaymentOperationRequests.*;
import com.moneybags.deposit.dto.PaymentOperationResponses.PaymentOperationView;
import com.moneybags.deposit.entity.*;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class PaymentOperationService {
    private static final String IDEMPOTENCY_SCOPE = "DEPOSIT_PAYMENT_COMMAND";
    private final FundReservationRepository reservationRepository;
    private final DepositAccountTransactionRepository transactionRepository;
    private final AccountBalanceRepository balanceRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final AuditLogRepository auditRepository;
    private final DepositAccountProperties properties;
    private final ObjectMapper objectMapper;

    public PaymentOperationService(FundReservationRepository reservationRepository,
                                   DepositAccountTransactionRepository transactionRepository,
                                   AccountBalanceRepository balanceRepository,
                                   IdempotencyRecordRepository idempotencyRepository,
                                   AuditLogRepository auditRepository, DepositAccountProperties properties,
                                   ObjectMapper objectMapper) {
        this.reservationRepository = reservationRepository;
        this.transactionRepository = transactionRepository;
        this.balanceRepository = balanceRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.auditRepository = auditRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentOperationView reserveBookTransfer(BookTransferReservationRequest request, String idempotencyKey,
                                                    String actor, String correlationId) {
        String hash = beginIdempotent("BOOK_RESERVE", idempotencyKey, request);
        Optional<FundReservation> existing = reservationRepository.findByPaymentIdAndOperationType(
                request.paymentId(), PaymentOperationType.BOOK_TRANSFER);
        if (existing.isPresent()) return verifyAndView(existing.get(), request.sourceAccountId(),
                request.targetAccountId(), null, request.amount(), request.currencyCode());
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_ACCOUNT_TRANSFER",
                    "Source and target accounts must be different");
        }
        AccountBalance source = lockBalance(request.sourceAccountId());
        AccountBalance target = lockBalance(request.targetAccountId());
        validateDebit(source, request.requestorCustomerId(), request.amount(), request.currencyCode());
        validateCredit(target, request.currencyCode());
        BigDecimal before = source.getAvailableBalance();
        String reservationId = UUID.randomUUID().toString();
        source.reserve(request.amount(), reservationId);
        FundReservation reservation = new FundReservation(reservationId, request.paymentId(),
                PaymentOperationType.BOOK_TRANSFER, request.sourceAccountId(), request.targetAccountId(), null,
                request.requestorCustomerId(), request.amount(), request.currencyCode(), expiry(request.expiresAt()));
        reservationRepository.save(reservation);
        recordTransaction(source, reservation, DepositTransactionType.PAYMENT_HOLD, before,
                source.getAvailableBalance(), correlationId);
        audit(reservation, "RESERVE_BOOK_TRANSFER", actor, correlationId);
        completeIdempotent(hash, reservationId, 201);
        return view(reservation);
    }

    @Transactional
    public PaymentOperationView reserveCardRepayment(CardRepaymentReservationRequest request, String idempotencyKey,
                                                     String actor, String correlationId) {
        String hash = beginIdempotent("CARD_RESERVE", idempotencyKey, request);
        Optional<FundReservation> existing = reservationRepository.findByPaymentIdAndOperationType(
                request.paymentId(), PaymentOperationType.CREDIT_CARD_REPAYMENT);
        if (existing.isPresent()) return verifyAndView(existing.get(), request.sourceAccountId(), null,
                request.creditCardAccountId(), request.amount(), request.currencyCode());
        AccountBalance source = lockBalance(request.sourceAccountId());
        validateDebit(source, request.requestorCustomerId(), request.amount(), request.currencyCode());
        BigDecimal before = source.getAvailableBalance();
        String reservationId = UUID.randomUUID().toString();
        source.reserve(request.amount(), reservationId);
        FundReservation reservation = new FundReservation(reservationId, request.paymentId(),
                PaymentOperationType.CREDIT_CARD_REPAYMENT, request.sourceAccountId(), null,
                request.creditCardAccountId(), request.requestorCustomerId(), request.amount(),
                request.currencyCode(), expiry(request.expiresAt()));
        reservationRepository.save(reservation);
        recordTransaction(source, reservation, DepositTransactionType.PAYMENT_HOLD, before,
                source.getAvailableBalance(), correlationId);
        audit(reservation, "RESERVE_CARD_REPAYMENT", actor, correlationId);
        completeIdempotent(hash, reservationId, 201);
        return view(reservation);
    }

    @Transactional
    public PaymentOperationView settleBookTransfer(String paymentId, SettlementRequest request,
                                                   String idempotencyKey, String actor, String correlationId) {
        String hash = beginIdempotent("BOOK_SETTLE", idempotencyKey, Map.of("paymentId", paymentId,
                "reservationId", request.reservationId()));
        FundReservation reservation = lockReservation(paymentId, PaymentOperationType.BOOK_TRANSFER);
        verifyReservationId(reservation, request.reservationId());
        if (reservation.getStatus() == ReservationStatus.SETTLED) return view(reservation);
        requireActiveNotExpired(reservation);
        Map<String, AccountBalance> balances = lockBalances(reservation.getSourceAccountId(),
                reservation.getTargetAccountId());
        AccountBalance source = balances.get(reservation.getSourceAccountId());
        AccountBalance target = balances.get(reservation.getTargetAccountId());
        BigDecimal sourceBefore = source.getLedgerBalance();
        BigDecimal targetBefore = target.getLedgerBalance();
        source.captureDebit(reservation.getAmount(), transactionReference(reservation, "DEBIT"));
        target.credit(reservation.getAmount(), transactionReference(reservation, "CREDIT"));
        recordTransaction(source, reservation, DepositTransactionType.DEBIT, sourceBefore,
                source.getLedgerBalance(), correlationId);
        recordTransaction(target, reservation, DepositTransactionType.CREDIT, targetBefore,
                target.getLedgerBalance(), correlationId);
        reservation.transitionTo(ReservationStatus.SETTLED);
        audit(reservation, "SETTLE_BOOK_TRANSFER", actor, correlationId);
        completeIdempotent(hash, reservation.getId(), 200);
        return view(reservation);
    }

    @Transactional
    public PaymentOperationView captureCardRepayment(String paymentId, SettlementRequest request,
                                                     String idempotencyKey, String actor, String correlationId) {
        String hash = beginIdempotent("CARD_CAPTURE", idempotencyKey, Map.of("paymentId", paymentId,
                "reservationId", request.reservationId()));
        FundReservation reservation = lockReservation(paymentId, PaymentOperationType.CREDIT_CARD_REPAYMENT);
        verifyReservationId(reservation, request.reservationId());
        if (reservation.getStatus() == ReservationStatus.CAPTURED) return view(reservation);
        requireActiveNotExpired(reservation);
        AccountBalance source = lockBalance(reservation.getSourceAccountId());
        BigDecimal before = source.getLedgerBalance();
        source.captureDebit(reservation.getAmount(), transactionReference(reservation, "DEBIT"));
        recordTransaction(source, reservation, DepositTransactionType.DEBIT, before,
                source.getLedgerBalance(), correlationId);
        reservation.transitionTo(ReservationStatus.CAPTURED);
        audit(reservation, "CAPTURE_CARD_REPAYMENT", actor, correlationId);
        completeIdempotent(hash, reservation.getId(), 200);
        return view(reservation);
    }

    @Transactional
    public PaymentOperationView release(String reservationId, ReleaseReservationRequest request,
                                        String idempotencyKey, String actor, String correlationId) {
        String hash = beginIdempotent("RELEASE", idempotencyKey, Map.of("reservationId", reservationId,
                "request", request));
        FundReservation reservation = reservationRepository.findLockedById(reservationId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "Reservation not found"));
        if (!reservation.getPaymentId().equals(request.paymentId())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_RESERVATION_MISMATCH",
                    "paymentId does not own the reservation");
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED ||
                reservation.getStatus() == ReservationStatus.EXPIRED) return view(reservation);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_FINALIZED",
                    "Captured or settled reservations cannot be released");
        }
        releaseActive(reservation, ReservationStatus.RELEASED, actor, correlationId);
        completeIdempotent(hash, reservationId, 200);
        return view(reservation);
    }

    @Transactional(readOnly = true)
    public PaymentOperationView get(String paymentId) {
        return reservationRepository.findByPaymentId(paymentId).map(this::view).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_OPERATION_NOT_FOUND", "Payment operation not found"));
    }

    @Scheduled(fixedDelayString = "${moneybags.deposit.reservation-expiry-interval-ms:60000}")
    @Transactional
    public void expireReservations() {
        reservationRepository.findTop100ByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, OffsetDateTime.now())
                .forEach(r -> reservationRepository.findLockedById(r.getId()).ifPresent(locked -> {
                    if (locked.getStatus() == ReservationStatus.ACTIVE && locked.getExpiresAt().isBefore(OffsetDateTime.now())) {
                        releaseActive(locked, ReservationStatus.EXPIRED, "reservation-expiry", "scheduled-expiry");
                    }
                }));
    }

    private void releaseActive(FundReservation reservation, ReservationStatus target, String actor,
                               String correlationId) {
        AccountBalance source = lockBalance(reservation.getSourceAccountId());
        BigDecimal before = source.getAvailableBalance();
        source.release(reservation.getAmount(), transactionReference(reservation, target.name()));
        recordTransaction(source, reservation, DepositTransactionType.HOLD_RELEASE, before,
                source.getAvailableBalance(), correlationId);
        reservation.transitionTo(target);
        audit(reservation, target == ReservationStatus.EXPIRED ? "EXPIRE_RESERVATION" : "RELEASE_RESERVATION",
                actor, correlationId);
    }

    private void validateDebit(AccountBalance balance, String customerId, BigDecimal amount, String currency) {
        DepositAccount account = balance.getAccount();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DEBIT_NOT_ALLOWED", "Source account is not active");
        }
        boolean authorized = account.getHolders().stream().anyMatch(h -> h.getCustomerId().equals(customerId)
                && h.getStatus() == RecordStatus.ACTIVE);
        if (!authorized) throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_ACCESS_DENIED",
                "Requestor is not an active account holder");
        if (!balance.getCurrencyCode().equals(currency)) currencyMismatch();
        if (balance.getAvailableBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_AVAILABLE_BALANCE",
                    "Available balance is insufficient");
        }
    }

    private void validateCredit(AccountBalance balance, String currency) {
        AccountStatus status = balance.getAccount().getStatus();
        if (status != AccountStatus.ACTIVE && status != AccountStatus.BLOCKED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CREDIT_NOT_ALLOWED",
                    "Target account cannot receive credits");
        }
        if (!balance.getCurrencyCode().equals(currency)) currencyMismatch();
    }

    private void currencyMismatch() {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH",
                "Payment currency does not match the deposit account");
    }

    private AccountBalance lockBalance(String accountId) {
        return balanceRepository.findByAccountIdForUpdate(accountId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Deposit account not found"));
    }

    private Map<String, AccountBalance> lockBalances(String firstId, String secondId) {
        List<String> ids = new ArrayList<>(List.of(firstId, secondId));
        ids.sort(String::compareTo);
        Map<String, AccountBalance> result = new HashMap<>();
        ids.forEach(id -> result.put(id, lockBalance(id)));
        return result;
    }

    private FundReservation lockReservation(String paymentId, PaymentOperationType operationType) {
        return reservationRepository.findLockedByPaymentIdAndOperationType(paymentId, operationType).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_OPERATION_NOT_FOUND", "Payment operation not found"));
    }

    private void requireActiveNotExpired(FundReservation reservation) {
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_FINALIZED", "Reservation is already finalized");
        }
        if (!reservation.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "RESERVATION_EXPIRED", "Reservation has expired");
        }
    }

    private void verifyReservationId(FundReservation reservation, String id) {
        if (!reservation.getId().equals(id)) throw new ApiException(HttpStatus.CONFLICT,
                "PAYMENT_RESERVATION_MISMATCH", "reservationId does not belong to paymentId");
    }

    private OffsetDateTime expiry(OffsetDateTime requested) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime value = requested == null ? now.plusMinutes(5) : requested;
        if (!value.isAfter(now) || value.isAfter(now.plusMinutes(30))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESERVATION_EXPIRY",
                    "expiresAt must be within the next 30 minutes");
        }
        return value;
    }

    private PaymentOperationView verifyAndView(FundReservation r, String source, String target, String external,
                                               BigDecimal amount, String currency) {
        if (!Objects.equals(r.getSourceAccountId(), source) || !Objects.equals(r.getTargetAccountId(), target)
                || !Objects.equals(r.getExternalTargetId(), external) || r.getAmount().compareTo(amount) != 0
                || !r.getCurrencyCode().equals(currency)) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ID_REUSED",
                    "paymentId was already used with different payment details");
        }
        return view(r);
    }

    private void recordTransaction(AccountBalance balance, FundReservation reservation,
                                   DepositTransactionType type, BigDecimal before, BigDecimal after,
                                   String correlationId) {
        transactionRepository.save(new DepositAccountTransaction(UUID.randomUUID().toString(),
                balance.getAccountId(), reservation.getPaymentId(), reservation.getId(), type,
                reservation.getOperationType(), reservation.getAmount(), reservation.getCurrencyCode(),
                before, after, correlationId));
    }

    private PaymentOperationView view(FundReservation r) {
        List<String> transactionIds = transactionRepository.findByPaymentIdOrderByCreatedAtAsc(r.getPaymentId())
                .stream().map(DepositAccountTransaction::getId).toList();
        return new PaymentOperationView(r.getId(), r.getPaymentId(), r.getOperationType(), r.getStatus(),
                r.getSourceAccountId(), r.getTargetAccountId(), r.getExternalTargetId(), r.getAmount(),
                r.getCurrencyCode(), r.getExpiresAt(), transactionIds);
    }

    private void audit(FundReservation r, String action, String actor, String correlationId) {
        auditRepository.save(new AuditLog(UUID.randomUUID().toString(), r.getSourceAccountId(), action, "SUCCESS",
                actor, "SERVICE", r.getStatus().name(), null, Hashing.sha256(r.getPaymentId()), correlationId));
    }

    private String transactionReference(FundReservation r, String suffix) {
        return r.getPaymentId() + ":" + suffix;
    }

    private String beginIdempotent(String command, String key, Object payload) {
        if (key == null || key.isBlank() || key.length() > 128) throw new ApiException(HttpStatus.BAD_REQUEST,
                "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required and must be at most 128 characters");
        String keyHash = Hashing.sha256(command + ":" + key);
        String requestHash = Hashing.sha256(json(payload));
        Optional<IdempotencyRecord> existing = idempotencyRepository.findByScopeAndKeyHash(IDEMPOTENCY_SCOPE, keyHash);
        if (existing.isPresent() && !existing.get().getRequestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was used with a different command payload");
        }
        if (existing.isEmpty()) idempotencyRepository.save(new IdempotencyRecord(UUID.randomUUID().toString(),
                IDEMPOTENCY_SCOPE, keyHash, requestHash,
                OffsetDateTime.now().plusHours(properties.idempotencyTtlHours())));
        return keyHash;
    }

    private void completeIdempotent(String keyHash, String resourceId, int httpStatus) {
        idempotencyRepository.findByScopeAndKeyHash(IDEMPOTENCY_SCOPE, keyHash).ifPresent(r -> {
            r.setProcessingStatus("COMPLETED");
            r.setResourceId(resourceId);
            r.setHttpStatus(httpStatus);
        });
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot hash request", ex); }
    }
}
