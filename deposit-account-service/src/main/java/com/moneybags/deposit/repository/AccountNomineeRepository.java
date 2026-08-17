package com.moneybags.deposit.repository;

import com.moneybags.deposit.entity.AccountNominee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountNomineeRepository extends JpaRepository<AccountNominee, String> {
    List<AccountNominee> findByAccountId(String accountId);
    void deleteByAccountId(String accountId);
}

