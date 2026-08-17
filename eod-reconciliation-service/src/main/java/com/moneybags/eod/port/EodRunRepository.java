package com.moneybags.eod.port;

import com.moneybags.eod.domain.EodDomain.EodRun;

import java.time.LocalDate;
import java.util.Optional;

public interface EodRunRepository {
    Optional<EodRun> findById(String runId);
    Optional<EodRun> findByBusinessDate(LocalDate businessDate);
    Optional<EodRun> findByExceptionId(String exceptionId);
    EodRun save(EodRun run);
}
