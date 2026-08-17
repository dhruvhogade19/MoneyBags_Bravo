package com.moneybags.deposit.repository;

import com.moneybags.deposit.entity.AccountMandate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountMandateRepository extends JpaRepository<AccountMandate, String> {
    List<AccountMandate> findByAccountId(String accountId);
}

