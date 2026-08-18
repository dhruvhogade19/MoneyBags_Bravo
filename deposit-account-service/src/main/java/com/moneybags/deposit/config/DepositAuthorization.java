package com.moneybags.deposit.config;

import com.moneybags.deposit.domain.DomainTypes.HolderRole;
import com.moneybags.deposit.domain.DomainTypes.RecordStatus;
import com.moneybags.deposit.dto.AccountRequests.OpenDepositAccountRequest;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositRequests.BookingRequest;
import com.moneybags.deposit.repository.DepositAccountRepository;
import com.moneybags.deposit.fixeddeposit.repository.FixedDepositRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("depositAuthorization")
public class DepositAuthorization {
    private final DepositAccountRepository accounts;
    private final FixedDepositRepository fixedDeposits;
    private final boolean securityEnabled;

    public DepositAuthorization(DepositAccountRepository accounts, FixedDepositRepository fixedDeposits,
                                @Value("${moneybags.security.enabled:true}") boolean securityEnabled) {
        this.accounts = accounts;
        this.fixedDeposits = fixedDeposits;
        this.securityEnabled = securityEnabled;
    }

    public boolean canUseCustomer(Authentication authentication, String customerId) {
        return !securityEnabled || privileged(authentication) || owns(authentication, customerId);
    }

    public boolean canSearch(Authentication authentication, String customerId) {
        return !securityEnabled || privileged(authentication)
                || (customerId != null && owns(authentication, customerId));
    }

    /** Recipient lookup exposes no balance, holder, or personally identifying data. */
    public boolean canLookupRecipient(Authentication authentication) {
        return !securityEnabled || privileged(authentication) || customerId(authentication) != null;
    }

    public boolean canOpen(Authentication authentication, OpenDepositAccountRequest request) {
        if (!securityEnabled || privileged(authentication)) return true;
        String customerId = customerId(authentication);
        return customerId != null && customerId.equals(request.primaryCustomerId())
                && request.customerIds().contains(customerId);
    }

    public boolean canBook(Authentication authentication, BookingRequest request) {
        if (!securityEnabled || privileged(authentication)) return true;
        String customerId = customerId(authentication);
        return customerId != null && customerId.equals(request.primaryCustomerId())
                && request.customerIds().contains(customerId);
    }

    public boolean canAccessAccount(Authentication authentication, String accountId) {
        if (!securityEnabled || privileged(authentication)) return true;
        String customerId = customerId(authentication);
        return customerId != null && accounts.existsByIdAndHoldersCustomerIdAndHoldersStatus(
                accountId, customerId, RecordStatus.ACTIVE);
    }

    public boolean canManageAccount(Authentication authentication, String accountId) {
        if (!securityEnabled || privileged(authentication)) return true;
        String customerId = customerId(authentication);
        return customerId != null && accounts.existsByIdAndHoldersCustomerIdAndHoldersRoleAndHoldersStatus(
                accountId, customerId, HolderRole.PRIMARY, RecordStatus.ACTIVE);
    }

    public boolean canAccessFixedDeposit(Authentication authentication, String fdId) {
        if (!securityEnabled || privileged(authentication)) return true;
        String customerId = customerId(authentication);
        return customerId != null && fixedDeposits.existsByIdAndAccountHoldersCustomerIdAndAccountHoldersStatus(
                fdId, customerId, RecordStatus.ACTIVE);
    }

    private boolean privileged(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_BANK_ADMIN") || a.getAuthority().equals("SCOPE_account:admin")
                        || a.getAuthority().equals("SCOPE_fd:admin") || a.getAuthority().equals("SCOPE_account:service")
                        || a.getAuthority().equals("SCOPE_deposit-payment:write"));
    }

    private boolean owns(Authentication authentication, String customerId) {
        String claim = customerId(authentication);
        return claim != null && claim.equals(customerId);
    }

    private String customerId(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getClaimAsString("customer_id") : null;
    }
}
