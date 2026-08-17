package com.moneybags.deposit.fixeddeposit.repository;

import com.moneybags.deposit.domain.DomainTypes.FixedDepositStatus;
import com.moneybags.deposit.domain.DomainTypes.RecordStatus;
import com.moneybags.deposit.fixeddeposit.entity.FixedDeposit;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FixedDepositRepository extends JpaRepository<FixedDeposit, String> {
    boolean existsByIdAndAccountHoldersCustomerIdAndAccountHoldersStatus(
            String id, String customerId, RecordStatus status);
    @EntityGraph(attributePaths = {"account", "account.holders"})
    Optional<FixedDeposit> findDetailedById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FixedDeposit f join fetch f.account where f.id=:id")
    Optional<FixedDeposit> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FixedDeposit f join fetch f.account where f.account.id=:accountId")
    Optional<FixedDeposit> findByAccountIdForUpdate(@Param("accountId") String accountId);

    @Query("select distinct f from FixedDeposit f join f.account a join a.holders h where " +
            "(:customerId is null or h.customerId=:customerId) and (:status is null or f.status=:status) and " +
            "(:maturingBefore is null or f.maturityDate<=:maturingBefore)")
    Page<FixedDeposit> search(@Param("customerId") String customerId, @Param("status") FixedDepositStatus status,
                              @Param("maturingBefore") LocalDate maturingBefore, Pageable pageable);

    @Query("select f from FixedDeposit f where f.status=:status and (f.lastAccrualDate is null or f.lastAccrualDate<:date) and f.valueDate<=:date")
    List<FixedDeposit> findAccrualCandidates(@Param("status") FixedDepositStatus status, @Param("date") LocalDate date);
    List<FixedDeposit> findByStatusAndMaturityDateLessThanEqual(FixedDepositStatus status, LocalDate date);

    long countByStatus(FixedDepositStatus status);
}
