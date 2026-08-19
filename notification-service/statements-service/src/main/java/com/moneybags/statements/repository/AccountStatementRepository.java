package com.moneybags.statements.repository;

import com.moneybags.statements.entity.AccountStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface AccountStatementRepository extends JpaRepository<AccountStatement, String> {
    Optional<AccountStatement> findByAccountReferenceAndAccountTypeAndPeriodStartAndPeriodEnd(
            String accountReference, String accountType, LocalDate periodStart, LocalDate periodEnd);
}
