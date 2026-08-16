package com.moneybags.deposit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moneybags.deposit.domain.DomainTypes.HolderRole;
import com.moneybags.deposit.domain.DomainTypes.RecordStatus;
import com.moneybags.deposit.fixeddeposit.repository.FixedDepositRepository;
import com.moneybags.deposit.repository.DepositAccountRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class DepositAuthorizationTest {
    @Mock DepositAccountRepository accounts;
    @Mock FixedDepositRepository fixedDeposits;

    @Test
    void consumerReadsOnlyAccountsWhereTheyAreAnActiveHolder() {
        var authorization = new DepositAuthorization(accounts, fixedDeposits, true);
        when(accounts.existsByIdAndHoldersCustomerIdAndHoldersStatus("A-1", "C-1", RecordStatus.ACTIVE))
                .thenReturn(true);

        assertThat(authorization.canAccessAccount(token("C-1", "SCOPE_account:read"), "A-1")).isTrue();
        verify(accounts).existsByIdAndHoldersCustomerIdAndHoldersStatus("A-1", "C-1", RecordStatus.ACTIVE);
    }

    @Test
    void consumerManagesOnlyAccountsWhereTheyAreTheActivePrimaryHolder() {
        var authorization = new DepositAuthorization(accounts, fixedDeposits, true);
        when(accounts.existsByIdAndHoldersCustomerIdAndHoldersRoleAndHoldersStatus(
                "A-2", "C-2", HolderRole.PRIMARY, RecordStatus.ACTIVE)).thenReturn(false);

        assertThat(authorization.canManageAccount(token("C-2", "SCOPE_account:write"), "A-2")).isFalse();
    }

    @Test
    void bankAdministratorBypassesCustomerOwnershipLookup() {
        var authorization = new DepositAuthorization(accounts, fixedDeposits, true);
        assertThat(authorization.canAccessAccount(token(null, "ROLE_BANK_ADMIN"), "A-3")).isTrue();
    }

    private static JwtAuthenticationToken token(String customerId, String authority) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject("user");
        if (customerId != null) jwt.claim("customer_id", customerId);
        return new JwtAuthenticationToken(jwt.build(), List.of(new SimpleGrantedAuthority(authority)));
    }
}
