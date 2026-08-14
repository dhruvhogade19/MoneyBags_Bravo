package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.GlAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GlAccountRepository extends JpaRepository<GlAccount, String> {
    Optional<GlAccount> findByGlCode(String glCode);
    boolean existsByGlCode(String glCode);
}
