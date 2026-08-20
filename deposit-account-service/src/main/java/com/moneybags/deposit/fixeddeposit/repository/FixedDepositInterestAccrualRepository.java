package com.moneybags.deposit.fixeddeposit.repository;
import com.moneybags.deposit.fixeddeposit.entity.FixedDepositInterestAccrual;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface FixedDepositInterestAccrualRepository extends JpaRepository<FixedDepositInterestAccrual, String> {
    boolean existsByFixedDepositIdAndBusinessDate(String fdId, LocalDate date);
    Optional<FixedDepositInterestAccrual> findByFixedDepositIdAndBusinessDate(String fdId, LocalDate date);
    List<FixedDepositInterestAccrual> findByFixedDepositIdOrderByBusinessDateAsc(String fdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from FixedDepositInterestAccrual a
            where a.businessDate<=:businessDate
              and (a.accountingPostingStatus is null or trim(a.accountingPostingStatus)=''
                   or upper(a.accountingPostingStatus) in ('PENDING','FAILED'))
            order by a.fixedDepositId, a.businessDate, a.id
            """)
    List<FixedDepositInterestAccrual> findPendingAccountingPostingsThrough(
            @Param("businessDate") LocalDate businessDate);

    @Query("""
            select a from FixedDepositInterestAccrual a
            where a.businessDate<=:businessDate and upper(a.accountingPostingStatus)='REVIEW_REQUIRED'
            order by a.fixedDepositId, a.businessDate, a.id
            """)
    List<FixedDepositInterestAccrual> findAccountingReviewRequiredThrough(
            @Param("businessDate") LocalDate businessDate);
}
