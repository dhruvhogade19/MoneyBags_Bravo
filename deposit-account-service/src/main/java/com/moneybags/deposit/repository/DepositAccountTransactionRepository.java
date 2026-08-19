package com.moneybags.deposit.repository;

import com.moneybags.deposit.domain.DomainTypes.DepositTransactionType;
import com.moneybags.deposit.entity.DepositAccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DepositAccountTransactionRepository extends JpaRepository<DepositAccountTransaction, String> {
    boolean existsByPaymentIdAndTransactionType(String paymentId, DepositTransactionType transactionType);
    List<DepositAccountTransaction> findByPaymentIdOrderByCreatedAtAsc(String paymentId);
    Page<DepositAccountTransaction> findByAccountIdAndTransactionTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            String accountId, Collection<DepositTransactionType> transactionTypes,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);
    Optional<DepositAccountTransaction> findFirstByAccountIdAndTransactionTypeInAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            String accountId, Collection<DepositTransactionType> transactionTypes, OffsetDateTime from);
}
