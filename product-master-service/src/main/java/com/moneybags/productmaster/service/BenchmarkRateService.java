package com.moneybags.productmaster.service;

import com.moneybags.productmaster.api.PricingDtos.*;
import com.moneybags.productmaster.entity.BenchmarkRate;
import com.moneybags.productmaster.exception.ProductExceptions.BusinessValidationException;
import com.moneybags.productmaster.repository.BenchmarkRateRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BenchmarkRateService {
    private final BenchmarkRateRepository repository;

    public BenchmarkRateService(BenchmarkRateRepository repository) {
        this.repository = repository;
    }

    public BenchmarkRateResponse create(BenchmarkRateRequest request) {
        String code = request.benchmarkCode().toUpperCase();
        if (request.effectiveTo() != null
                && !request.effectiveTo().isAfter(request.effectiveFrom())) {
            fail("effectiveTo must be later than effectiveFrom");
        }
        if (repository.existsByBenchmarkCodeAndEffectiveFrom(code, request.effectiveFrom())) {
            fail("A benchmark rate already starts on this date");
        }
        List<BenchmarkRate> history = repository.findAllByBenchmarkCodeOrderByEffectiveFromDesc(code);
        if (!history.isEmpty()) {
            BenchmarkRate latest = history.getFirst();
            if (!latest.getEffectiveFrom().isBefore(request.effectiveFrom())) {
                fail("Benchmark rates must be published in effective-date order");
            }
            if (latest.getEffectiveTo() == null
                    || !latest.getEffectiveTo().isBefore(request.effectiveFrom())) {
                latest.setEffectiveTo(request.effectiveFrom().minusDays(1));
            }
        }
        BenchmarkRate rate = new BenchmarkRate();
        rate.setBenchmarkCode(code);
        rate.setAnnualRate(request.annualRate());
        rate.setEffectiveFrom(request.effectiveFrom());
        rate.setEffectiveTo(request.effectiveTo());
        rate.setCreatedBy(request.createdBy());
        return response(repository.save(rate));
    }

    @Transactional(readOnly = true)
    public BenchmarkRate effective(String code, LocalDate effectiveOn) {
        String normalized = code.toUpperCase();
        return repository.findEffective(normalized, effectiveOn).stream().findFirst()
                .orElseThrow(() -> new BusinessValidationException(
                        List.of("No " + normalized + " benchmark rate is effective on " + effectiveOn)));
    }

    @Transactional(readOnly = true)
    public BenchmarkRateResponse getEffective(String code, LocalDate effectiveOn) {
        return response(effective(code, effectiveOn));
    }

    @Transactional(readOnly = true)
    public List<BenchmarkRateResponse> history(String code) {
        return repository.findAllByBenchmarkCodeOrderByEffectiveFromDesc(code.toUpperCase())
                .stream().map(this::response).toList();
    }

    private BenchmarkRateResponse response(BenchmarkRate rate) {
        return new BenchmarkRateResponse(rate.getId(), rate.getBenchmarkCode(), rate.getAnnualRate(),
                rate.getEffectiveFrom(), rate.getEffectiveTo(), rate.getCreatedAt(), rate.getCreatedBy());
    }

    private void fail(String message) {
        throw new BusinessValidationException(List.of(message));
    }
}
