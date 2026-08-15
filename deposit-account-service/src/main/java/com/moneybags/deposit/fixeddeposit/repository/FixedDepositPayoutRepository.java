package com.moneybags.deposit.fixeddeposit.repository;
import com.moneybags.deposit.fixeddeposit.entity.FixedDepositPayout;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FixedDepositPayoutRepository extends JpaRepository<FixedDepositPayout, String> {
    boolean existsBySourceReference(String sourceReference);
}
