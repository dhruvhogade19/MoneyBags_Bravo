package com.moneybags.deposit.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "true", matchIfMissing = true)
public class StubAccountingFixedDepositPostingGateway implements AccountingFixedDepositPostingGateway {
    private final List<Invocation> invocations = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, PostingResponse> postings = new ConcurrentHashMap<>();

    @Override
    public PostingResponse post(FixedDepositPosting request, String idempotencyKey, String correlationId) {
        invocations.add(new Invocation(request, idempotencyKey, correlationId));
        BigDecimal totalDebit = request.components().stream().map(PostingComponent::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4);
        String stableId = UUID.nameUUIDFromBytes(request.postingReference().getBytes(StandardCharsets.UTF_8)).toString();
        PostingResponse created = new PostingResponse("JRN-STUB-" + stableId, "POSTED", totalDebit,
                false, correlationId);
        PostingResponse existing = postings.putIfAbsent(request.postingReference(), created);
        return existing == null ? created : new PostingResponse(existing.journalNumber(), existing.status(),
                existing.totalDebit(), true, existing.correlationId());
    }

    public List<Invocation> invocations() {
        return List.copyOf(invocations);
    }

    public void clear() {
        invocations.clear();
        postings.clear();
    }

    public record Invocation(FixedDepositPosting request, String idempotencyKey, String correlationId) { }
}
