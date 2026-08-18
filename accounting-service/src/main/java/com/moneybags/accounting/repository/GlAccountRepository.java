package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.GlAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.moneybags.accounting.domain.DomainTypes.*;

import java.util.Optional;

public interface GlAccountRepository extends JpaRepository<GlAccount, String> {
    Optional<GlAccount> findByGlCode(String glCode);
    boolean existsByGlCode(String glCode);
    @Query("select g from GlAccount g where (:search is null or lower(g.glCode) like lower(concat('%',:search,'%')) " +
            "or lower(g.name) like lower(concat('%',:search,'%'))) and (:accountType is null or g.accountType=:accountType) " +
            "and (:status is null or g.status=:status) and (:currency is null or g.currencyCode=:currency)")
    Page<GlAccount> search(@Param("search") String search, @Param("accountType") GlAccountType accountType,
                           @Param("status") RecordStatus status, @Param("currency") String currency,
                           Pageable pageable);
}
