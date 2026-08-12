package com.moneybags.deposit.repository;

import com.moneybags.deposit.domain.DomainTypes.DepositTransactionType;
import com.moneybags.deposit.entity.DepositAccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepositAccountTransactionRepository extends JpaRepository<DepositAccountTransaction, String> {
    boolean existsByPaymentIdAndTransactionType(String paymentId, DepositTransactionType transactionType);
    List<DepositAccountTransaction> findByPaymentIdOrderByCreatedAtAsc(String paymentId);
}
