package com.moneybags.deposit.closure.repository;
import com.moneybags.deposit.closure.entity.FixedDepositPrematureClosureCalculation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface FixedDepositPrematureClosureCalculationRepository extends JpaRepository<FixedDepositPrematureClosureCalculation,String>{Optional<FixedDepositPrematureClosureCalculation> findByClosureRequestId(String id);}
