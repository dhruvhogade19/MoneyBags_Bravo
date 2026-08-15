package com.moneybags.accounting.repository;

import com.moneybags.accounting.domain.DomainTypes.AccountType;
import com.moneybags.accounting.entity.SubledgerAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubledgerAccountRepository extends JpaRepository<SubledgerAccount, String> {
    Optional<SubledgerAccount> findByAccountTypeAndAccountReference(AccountType type, String reference);
    List<SubledgerAccount> findByAccountReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SubledgerAccount a where a.accountType=:type and a.accountReference=:reference")
    Optional<SubledgerAccount> findForUpdate(@Param("type") AccountType type, @Param("reference") String reference);
}
