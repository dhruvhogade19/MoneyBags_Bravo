package com.moneybags.deposit.fixeddeposit.repository;
import com.moneybags.deposit.domain.DomainTypes.FixedDepositPayoutStatus;
import com.moneybags.deposit.fixeddeposit.entity.FixedDepositPayout;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface FixedDepositPayoutRepository extends JpaRepository<FixedDepositPayout, String> {
    boolean existsBySourceReference(String sourceReference);
    Optional<FixedDepositPayout> findBySourceReference(String sourceReference);
    Optional<FixedDepositPayout> findFirstByFixedDepositIdAndPayoutTypeOrderByCreatedAtDesc(
            String fixedDepositId, String payoutType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from FixedDepositPayout p, FixedDeposit f
            where p.fixedDepositId=f.id and f.maturityDate<=:businessDate
              and p.payoutType='MATURITY' and p.status=:status
              and (p.accountingPostingStatus is null or trim(p.accountingPostingStatus)=''
                   or upper(p.accountingPostingStatus) in ('PENDING','FAILED'))
            order by f.maturityDate, p.createdAt, p.id
            """)
    List<FixedDepositPayout> findPendingMaturityAccountingPostingsThrough(
            @Param("businessDate") LocalDate businessDate,
            @Param("status") FixedDepositPayoutStatus status);

    @Query("""
            select p from FixedDepositPayout p, FixedDeposit f
            where p.fixedDepositId=f.id and f.maturityDate<=:businessDate
              and p.payoutType='MATURITY' and upper(p.accountingPostingStatus)='REVIEW_REQUIRED'
            order by f.maturityDate, p.createdAt, p.id
            """)
    List<FixedDepositPayout> findMaturityAccountingReviewRequiredThrough(
            @Param("businessDate") LocalDate businessDate);
}
