package com.moneybags.deposit.fixeddeposit.repository;
import com.moneybags.deposit.fixeddeposit.entity.FixedDepositPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface FixedDepositPayoutRepository extends JpaRepository<FixedDepositPayout, String> {
    boolean existsBySourceReference(String sourceReference);
    Optional<FixedDepositPayout> findBySourceReference(String sourceReference);
}
