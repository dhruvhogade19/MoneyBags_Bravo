package com.moneybags.deposit.closure.repository;
import com.moneybags.deposit.closure.entity.AccountClosureCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AccountClosureCheckRepository extends JpaRepository<AccountClosureCheck,String>{List<AccountClosureCheck> findByClosureRequestIdOrderByCheckedAtAsc(String id);}
