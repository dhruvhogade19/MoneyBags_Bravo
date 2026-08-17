package com.moneybags.deposit.controller;

import com.moneybags.deposit.domain.DomainTypes.DepositTransactionType;
import com.moneybags.deposit.entity.DepositAccountTransaction;
import com.moneybags.deposit.repository.DepositAccountTransactionRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Read-only, service-only projection for the Statements service. */
@RestController
@Validated
@RequestMapping("/api/internal/deposit-accounts")
public class InternalStatementActivityController {
    private final DepositAccountTransactionRepository transactions;

    public InternalStatementActivityController(DepositAccountTransactionRepository transactions) {
        this.transactions = transactions;
    }

    @GetMapping("/{accountId}/statement-activities")
    public StatementActivityPage activity(@PathVariable String accountId,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "200") @Min(1) @Max(500) int size) {
        if (to.isBefore(from)) throw new IllegalArgumentException("to must not be before from");
        OffsetDateTime fromInclusive = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toExclusive = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        Page<DepositAccountTransaction> result = transactions
                .findByAccountIdAndTransactionTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        accountId, List.of(DepositTransactionType.DEBIT, DepositTransactionType.CREDIT),
                        fromInclusive, toExclusive, PageRequest.of(page, size));
        return new StatementActivityPage(result.getContent().stream().map(this::view).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    private StatementActivityView view(DepositAccountTransaction value) {
        return new StatementActivityView(value.getId(), value.getPaymentId(), value.getTransactionType().name(),
                value.getOperationType().name(), value.getAmount(), value.getCurrencyCode().trim(),
                value.getBalanceBefore(), value.getBalanceAfter(), value.getCreatedAt());
    }

    public record StatementActivityView(String transactionId, String paymentId, String direction,
                                        String operationType, BigDecimal amount, String currency,
                                        BigDecimal balanceBefore, BigDecimal balanceAfter,
                                        OffsetDateTime createdAt) {}
    public record StatementActivityPage(List<StatementActivityView> content, int page, int size,
                                        long totalElements, int totalPages) {}
}
