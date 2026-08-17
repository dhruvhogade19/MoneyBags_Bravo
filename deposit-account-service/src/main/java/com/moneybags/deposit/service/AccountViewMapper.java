package com.moneybags.deposit.service;

import com.moneybags.deposit.dto.AccountResponses.*;
import com.moneybags.deposit.entity.*;
import com.moneybags.deposit.repository.AccountLimitRepository;
import com.moneybags.deposit.repository.AccountMandateRepository;
import com.moneybags.deposit.repository.AccountNomineeRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Component
public class AccountViewMapper {
    private final AccountLimitRepository limitRepository;
    private final AccountNomineeRepository nomineeRepository;
    private final AccountMandateRepository mandateRepository;

    public AccountViewMapper(AccountLimitRepository limitRepository, AccountNomineeRepository nomineeRepository,
                             AccountMandateRepository mandateRepository) {
        this.limitRepository = limitRepository;
        this.nomineeRepository = nomineeRepository;
        this.mandateRepository = mandateRepository;
    }

    public AccountDetailView detail(DepositAccount account) {
        List<HolderView> holders = account.getHolders().stream().map(h -> new HolderView(
                h.getCustomerId(), h.getRole(), h.getAuthorizationType(), h.getOwnershipPercentage(), h.getStatus().name())).toList();
        List<LimitView> limits = limitRepository.findByAccountId(account.getId()).stream().map(this::limit).toList();
        List<NomineeView> nominees = nomineeRepository.findByAccountId(account.getId()).stream().map(n ->
                new NomineeView(n.getId(), n.getCustomerReference(), n.getRelationshipCode(),
                        n.getAllocationPercentage(), n.getStatus().name())).toList();
        List<MandateView> mandates = mandateRepository.findByAccountId(account.getId()).stream().map(m ->
                new MandateView(m.getId(), m.getAuthorizedCustomerId(), m.getMandateType(), m.getStatus().name(),
                        m.getValidFrom(), m.getValidTo())).toList();
        return new AccountDetailView(account.getId(), mask(account.getAccountNumber()), account.getStatus(),
                new ProductView(account.getProductId(), account.getProductVersion(), account.getProductNameSnapshot()),
                account.getCurrencyCode(), account.getServicingBranchId(), account.getOperatingInstruction().name(),
                holders, nominees, mandates, limits, balance(account.getBalance()), account.getOpenedAt(),
                account.getCreatedAt(), account.getVersion());
    }

    public AccountSummaryView summary(DepositAccount account) {
        AccountBalance b = account.getBalance();
        return new AccountSummaryView(account.getId(), mask(account.getAccountNumber()),
                account.getProductNameSnapshot(), account.getProductSubtype(), account.getCurrencyCode(), account.getStatus(),
                b == null ? null : b.getAvailableBalance(), b == null ? null : b.getAsOf(),
                account.getServicingBranchId(), account.getVersion());
    }

    public BalanceView balance(AccountBalance value) {
        if (value == null) return null;
        boolean stale = Duration.between(value.getAsOf(), OffsetDateTime.now()).abs().toSeconds() > 30;
        return new BalanceView(value.getLedgerBalance(), value.getAvailableBalance(), value.getBlockedAmount(),
                value.getCurrencyCode(), value.getAsOf(), value.getProjectionVersion(), stale);
    }

    public LimitView limit(AccountLimit value) {
        return new LimitView(value.getLimitType(), value.getAmount(), value.getCurrencyCode(),
                value.getEffectiveFrom(), value.getEffectiveTo());
    }

    private String mask(String accountNumber) {
        int visible = Math.min(4, accountNumber.length());
        return "X".repeat(Math.max(0, accountNumber.length() - visible))
                + accountNumber.substring(accountNumber.length() - visible);
    }
}
