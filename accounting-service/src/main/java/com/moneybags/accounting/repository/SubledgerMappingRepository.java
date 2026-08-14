package com.moneybags.accounting.repository;

import com.moneybags.accounting.domain.DomainTypes.RecordStatus;
import com.moneybags.accounting.entity.SubledgerMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubledgerMappingRepository extends JpaRepository<SubledgerMapping, String> {
    List<SubledgerMapping> findByMappingCodeAndCurrencyCodeAndStatus(String mappingCode, String currencyCode,
                                                                     RecordStatus status);
}
