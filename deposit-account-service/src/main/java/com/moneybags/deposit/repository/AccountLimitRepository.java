package com.moneybags.deposit.repository;

import com.moneybags.deposit.domain.DomainTypes.LimitType;
import com.moneybags.deposit.entity.AccountLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountLimitRepository extends JpaRepository<AccountLimit, String> {
    List<AccountLimit> findByAccountId(String accountId);
    Optional<AccountLimit> findFirstByAccountIdAndLimitTypeOrderByEffectiveFromDesc(String accountId, LimitType type);
}

