package com.moneybags.productmaster.repository;

import com.moneybags.productmaster.entity.BenchmarkRate;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BenchmarkRateRepository extends JpaRepository<BenchmarkRate, String> {
    boolean existsByBenchmarkCodeAndEffectiveFrom(String benchmarkCode, LocalDate effectiveFrom);
    List<BenchmarkRate> findAllByBenchmarkCodeOrderByEffectiveFromDesc(String benchmarkCode);

    @Query("""
           select r from BenchmarkRate r
           where r.benchmarkCode = :code
             and r.effectiveFrom <= :on
             and (r.effectiveTo is null or r.effectiveTo >= :on)
           order by r.effectiveFrom desc
           """)
    List<BenchmarkRate> findEffective(@Param("code") String code, @Param("on") LocalDate on);
}
