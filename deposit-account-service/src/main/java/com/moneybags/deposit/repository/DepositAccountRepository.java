package com.moneybags.deposit.repository;

import com.moneybags.deposit.domain.DomainTypes.AccountStatus;
import com.moneybags.deposit.domain.DomainTypes.HolderRole;
import com.moneybags.deposit.domain.DomainTypes.RecordStatus;
import com.moneybags.deposit.entity.DepositAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DepositAccountRepository extends JpaRepository<DepositAccount, String> {
    boolean existsByIdAndHoldersCustomerIdAndHoldersStatus(String id, String customerId, RecordStatus status);
    boolean existsByIdAndHoldersCustomerIdAndHoldersRoleAndHoldersStatus(
            String id, String customerId, HolderRole role, RecordStatus status);
    boolean existsByAccountNumber(String accountNumber);

    long countByCurrencyCodeAndStatus(String currencyCode, AccountStatus status);

    @Query("select count(distinct r.sourceAccountId) from FundReservation r where r.status = com.moneybags.deposit.domain.DomainTypes.ReservationStatus.ACTIVE")
    long countAccountsWithActiveReservations();

    @EntityGraph(attributePaths = {"holders", "balance"})
    Optional<DepositAccount> findDetailedById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DepositAccount a where a.id=:id")
    Optional<DepositAccount> findByIdForUpdate(@Param("id") String id);

    @Query(value = "select distinct a from DepositAccount a join a.holders h " +
            "where (:customerId is null or h.customerId = :customerId) " +
            "and (:status is null or a.status = :status)",
            countQuery = "select count(distinct a.id) from DepositAccount a join a.holders h " +
                    "where (:customerId is null or h.customerId = :customerId) " +
                    "and (:status is null or a.status = :status)")
    Page<DepositAccount> search(@Param("customerId") String customerId,
                                @Param("status") AccountStatus status,
                                Pageable pageable);
}
