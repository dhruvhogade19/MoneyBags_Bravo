package com.moneybags.deposit.fixeddeposit.repository;
import com.moneybags.deposit.fixeddeposit.entity.FixedDepositRateSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FixedDepositRateSnapshotRepository extends JpaRepository<FixedDepositRateSnapshot, String> {}
