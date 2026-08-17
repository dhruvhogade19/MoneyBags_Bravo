package com.moneybags.eod.adapter.memory;

import com.moneybags.eod.config.EodProperties;
import com.moneybags.eod.domain.EodDomain.BusinessDateState;
import com.moneybags.eod.domain.EodDomain.EodRun;
import com.moneybags.eod.port.BusinessDateRepository;
import com.moneybags.eod.port.EodRunRepository;
import com.moneybags.eod.port.IdempotencyStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class InMemoryStores {
    private InMemoryStores() {}

    @Component
    @ConditionalOnProperty(name = "moneybags.eod.persistence", havingValue = "memory", matchIfMissing = true)
    public static class Runs implements EodRunRepository {
        private final Map<String, EodRun> runs = new ConcurrentHashMap<>();
        public Optional<EodRun> findById(String runId) { return Optional.ofNullable(runs.get(runId)); }
        public Optional<EodRun> findByBusinessDate(LocalDate date) { return runs.values().stream().filter(r -> r.businessDate().equals(date)).findFirst(); }
        public Optional<EodRun> findByExceptionId(String id) {
            return runs.values().stream().filter(r -> r.exceptions().stream().anyMatch(e -> e.exceptionId().equals(id))).findFirst();
        }
        public EodRun save(EodRun run) { runs.put(run.runId(), run); return run; }
    }

    @Component
    @ConditionalOnProperty(name = "moneybags.eod.persistence", havingValue = "memory", matchIfMissing = true)
    public static class BusinessDates implements BusinessDateRepository {
        private final AtomicReference<BusinessDateState> current;
        public BusinessDates(EodProperties properties, Clock clock) {
            current = new AtomicReference<>(new BusinessDateState(properties.getInitialBusinessDate(), Instant.now(clock)));
        }
        public Optional<BusinessDateState> current() { return Optional.ofNullable(current.get()); }
        public BusinessDateState save(BusinessDateState state) { current.set(state); return state; }
    }

    @Component
    @ConditionalOnProperty(name = "moneybags.eod.persistence", havingValue = "memory", matchIfMissing = true)
    public static class Idempotency implements IdempotencyStore {
        private final Map<String, Entry> entries = new ConcurrentHashMap<>();
        public Optional<Entry> find(String scope, String key) { return Optional.ofNullable(entries.get(scope + ":" + key)); }
        public void save(Entry entry) { entries.put(entry.scope() + ":" + entry.key(), entry); }
    }
}
