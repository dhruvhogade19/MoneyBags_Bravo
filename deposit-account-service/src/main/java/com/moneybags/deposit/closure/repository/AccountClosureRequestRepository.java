package com.moneybags.deposit.closure.repository;
import com.moneybags.deposit.closure.entity.AccountClosureRequest;
import com.moneybags.deposit.domain.DomainTypes.ClosureRequestStatus;
import com.moneybags.deposit.domain.DomainTypes.ClosureType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface AccountClosureRequestRepository extends JpaRepository<AccountClosureRequest,String>{
    List<AccountClosureRequest> findByAccountIdOrderByCreatedAtDesc(String accountId);
    boolean existsByAccountIdAndClosureType(String accountId, ClosureType closureType);
    @Query("select c from AccountClosureRequest c where c.accountId=:accountId and c.status not in :terminal")
    List<AccountClosureRequest> findActive(@Param("accountId")String accountId,@Param("terminal")Collection<ClosureRequestStatus> terminal);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from AccountClosureRequest c where c.id=:id")
    Optional<AccountClosureRequest> findByIdForUpdate(@Param("id")String id);
}
