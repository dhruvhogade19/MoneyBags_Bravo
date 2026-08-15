package com.moneybags.deposit.closure.repository;
import com.moneybags.deposit.closure.entity.AccountClosureSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AccountClosureSettlementRepository extends JpaRepository<AccountClosureSettlement,String>{
    Optional<AccountClosureSettlement> findByClosureRequestId(String id); boolean existsByTransactionReference(String ref);
}
