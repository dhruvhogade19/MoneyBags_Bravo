package com.moneybags.productmaster.api;

import com.moneybags.productmaster.api.PricingDtos.*;
import com.moneybags.productmaster.service.BenchmarkRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/benchmarks")
@Tag(name = "Treasury Benchmarks", description = "Benchmark rates used by product pricing")
public class BenchmarkRateController {
    private final BenchmarkRateService service;

    public BenchmarkRateController(BenchmarkRateService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BenchmarkRateResponse create(@Valid @RequestBody BenchmarkRateRequest request) {
        return service.create(request);
    }

    @GetMapping("/{benchmarkCode}/effective")
    @Operation(summary = "Get the benchmark rate effective on a date")
    public BenchmarkRateResponse effective(
            @PathVariable String benchmarkCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveOn) {
        return service.getEffective(benchmarkCode, effectiveOn);
    }

    @GetMapping("/{benchmarkCode}/history")
    public List<BenchmarkRateResponse> history(@PathVariable String benchmarkCode) {
        return service.history(benchmarkCode);
    }
}
