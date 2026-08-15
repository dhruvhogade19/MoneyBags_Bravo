package com.moneybags.deposit.fixeddeposit.repository;
import com.moneybags.deposit.fixeddeposit.entity.FixedDepositInterestAccrual;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface FixedDepositInterestAccrualRepository extends JpaRepository<FixedDepositInterestAccrual, String> {
    boolean existsByFixedDepositIdAndBusinessDate(String fdId, LocalDate date);
    List<FixedDepositInterestAccrual> findByFixedDepositIdOrderByBusinessDateAsc(String fdId);
}
