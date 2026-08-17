package com.moneybags.deposit.repository;

import com.moneybags.deposit.entity.AccountBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AccountBalance b join fetch b.account where b.accountId = :accountId")
    Optional<AccountBalance> findByAccountIdForUpdate(@Param("accountId") String accountId);
}
