package com.moneybags.accounting.repository;

import com.moneybags.accounting.domain.DomainTypes.RecordStatus;
import com.moneybags.accounting.entity.SubledgerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SubledgerMappingRepository extends JpaRepository<SubledgerMapping, String> {
    List<SubledgerMapping> findByMappingCodeAndCurrencyCodeAndStatus(String mappingCode, String currencyCode,
                                                                     RecordStatus status);
    @Query("select m from SubledgerMapping m where (:search is null or lower(m.mappingCode) like lower(concat('%',:search,'%')) " +
            "or lower(m.productCode) like lower(concat('%',:search,'%')) or lower(m.glCode) like lower(concat('%',:search,'%'))) " +
            "and (:glCode is null or m.glCode=:glCode) and (:status is null or m.status=:status) " +
            "and (:currency is null or m.currencyCode=:currency)")
    Page<SubledgerMapping> search(@Param("search") String search, @Param("glCode") String glCode,
                                  @Param("status") RecordStatus status, @Param("currency") String currency,
                                  Pageable pageable);
}
