package com.moneybags.deposit.dto;

import com.moneybags.deposit.domain.DomainTypes.AccountStatus;
import com.moneybags.deposit.domain.DomainTypes.HolderRole;
import com.moneybags.deposit.domain.DomainTypes.LimitType;
import com.moneybags.deposit.domain.DomainTypes.ProductSubtype;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class AccountResponses {
    private AccountResponses() {}

    public record HolderView(String customerId, HolderRole role, String authorizationType,
                             BigDecimal ownershipPercentage, String status) {}
    public record ProductView(String productId, Long version, String name) {}
    public record BalanceView(BigDecimal ledger, BigDecimal available, BigDecimal blocked,
                              String currency, OffsetDateTime asOf, long projectionVersion, boolean stale) {}
    public record LimitView(LimitType type, BigDecimal amount, String currency,
                            OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo) {}
    public record NomineeView(String nomineeId, String customerReference, String relationshipCode,
                              BigDecimal allocationPercentage, String status) {}
    public record MandateView(String mandateId, String authorizedCustomerId, String mandateType,
                              String status, OffsetDateTime validFrom, OffsetDateTime validTo) {}

    public record AccountSummaryView(String accountId, String maskedAccountNumber, String productName,
                                     ProductSubtype productSubtype, String currency, AccountStatus status, BigDecimal availableBalance,
                                     OffsetDateTime balanceAsOf, String servicingBranchId, long version) {}

    /** A deliberately limited account view used only to validate a transfer recipient. */
    public record RecipientAccountView(String accountId, String maskedAccountNumber, String productName,
                                       String currency) {}

    /** Returned only to an account holder or a privileged bank operator. */
    public record AccountNumberView(String accountNumber) {}

    public record AccountDetailView(String accountId, String maskedAccountNumber, AccountStatus status,
                                    ProductView product, String currency, String servicingBranchId,
                                    String operatingInstruction, List<HolderView> holders,
                                    List<NomineeView> nominees, List<MandateView> mandates, List<LimitView> limits,
                                    BalanceView balance, OffsetDateTime openedAt,
                                    OffsetDateTime createdAt, long version) {}

    public record StatusHistoryView(AccountStatus fromStatus, AccountStatus toStatus,
                                    String reasonCode, String reasonText, String changedBy,
                                    String actorType, OffsetDateTime changedAt, String correlationId) {}

    public record EligibilityResult(boolean eligible, String decisionCode, String productName,
                                    List<String> messages, OffsetDateTime evaluatedAt) {}

    public record AccountEligibilityView(String accountId, AccountStatus status, boolean debitAllowed,
                                         boolean creditAllowed, String currency, List<LimitView> limits,
                                         OffsetDateTime evaluatedAt) {}
}
