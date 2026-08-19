package com.moneybags.statements.repository;

import com.moneybags.statements.entity.AccountStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountStatementLineRepository extends JpaRepository<AccountStatementLine, String> {
    List<AccountStatementLine> findByStatementIdOrderBySequenceAsc(String statementId);
}
