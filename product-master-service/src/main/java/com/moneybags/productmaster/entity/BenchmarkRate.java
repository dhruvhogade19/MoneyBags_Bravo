package com.moneybags.productmaster.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "BENCHMARK_RATE",
       uniqueConstraints = @UniqueConstraint(name = "UK_BENCHMARK_CODE_FROM",
                                             columnNames = {"BENCHMARK_CODE", "EFFECTIVE_FROM"}))
public class BenchmarkRate {
    @Id
    @SequenceGenerator(name = "benchmarkRateIdGenerator", sequenceName = "SEQ_BENCHMARK_RATE_ID", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "benchmarkRateIdGenerator")
    @Column(name = "ID")
    private Long id;
    @Column(name = "BENCHMARK_CODE", nullable = false, length = 40)
    private String benchmarkCode;
    @Column(name = "ANNUAL_RATE", nullable = false, precision = 9, scale = 4)
    private BigDecimal annualRate;
    @Column(name = "EFFECTIVE_FROM", nullable = false)
    private LocalDate effectiveFrom;
    @Column(name = "EFFECTIVE_TO")
    private LocalDate effectiveTo;
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "CREATED_BY", nullable = false, length = 100)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getBenchmarkCode() { return benchmarkCode; }
    public void setBenchmarkCode(String value) { benchmarkCode = value; }
    public BigDecimal getAnnualRate() { return annualRate; }
    public void setAnnualRate(BigDecimal value) { annualRate = value; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate value) { effectiveFrom = value; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate value) { effectiveTo = value; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
}
