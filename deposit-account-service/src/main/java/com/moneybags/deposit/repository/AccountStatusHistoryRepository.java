package com.moneybags.deposit.repository;

import com.moneybags.deposit.entity.AccountStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory, String> {
    List<AccountStatusHistory> findByAccountIdOrderByChangedAtAsc(String accountId);
}
