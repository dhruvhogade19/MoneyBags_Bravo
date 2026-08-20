package com.moneybags.eod;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;

@Component
class EodFailurePolicy {
    private static final Set<String> TRANSIENT_HTTP_CODES = Set.of(
            "UPSTREAM_HTTP_408", "UPSTREAM_HTTP_425", "UPSTREAM_HTTP_429",
            "UPSTREAM_HTTP_500", "UPSTREAM_HTTP_502", "UPSTREAM_HTTP_503", "UPSTREAM_HTTP_504");
    private static final Set<String> BUSINESS_CODES = Set.of(
            "PAYMENTS_NOT_REOPENED", "CREDIT_CARD_NOT_READY",
            "DEPOSIT_NOT_READY", "BILL_CLOSE_FAILED", "TRIAL_BALANCE_UNBALANCED",
            "RECONCILIATION_EXCEPTION", "FIXED_DEPOSIT_POSTING_FAILED", "DELIVERY_FAILED",
            "PERIOD_NOT_CLOSED", "PERIOD_NOT_OPEN");

    private final boolean backoffEnabled;

    EodFailurePolicy(@Value("${moneybags.eod.retry.backoff-enabled:true}") boolean backoffEnabled) {
        this.backoffEnabled = backoffEnabled;
    }

    FailureDescriptor describe(Throwable throwable, StepDefinition step, int attempt) {
        String code;
        String message;
        Map<String, Object> sourceDetails;
        if (throwable instanceof PeerOperationException peer) {
            code = peer.code();
            message = peer.getMessage();
            sourceDetails = peer.details();
        } else {
            code = "ORCHESTRATION_ERROR";
            message = Objects.toString(throwable.getMessage(), throwable.getClass().getSimpleName());
            sourceDetails = Map.of();
        }

        FailureClass classification = classify(code, step);
        boolean retryable = classification == FailureClass.TRANSIENT && attempt < step.maxAttempts();
        Map<String, Object> details = new LinkedHashMap<>(sourceDetails);
        details.put("message", Objects.toString(message, ""));
        details.put("failureClass", classification.name());
        details.put("retryable", retryable);
        details.put("attempt", attempt);
        details.put("maxAttempts", step.maxAttempts());
        details.put("contractVersion", step.contractVersion());
        return new FailureDescriptor(code, message,
                Collections.unmodifiableMap(new LinkedHashMap<>(details)), classification, retryable);
    }

    void backoff(StepDefinition step, int completedAttempt) {
        if (!backoffEnabled || step.retryBackoffMs() == 0) return;
        long multiplier = 1L << Math.min(Math.max(completedAttempt - 1, 0), 4);
        long delay = Math.min(step.retryBackoffMs() * multiplier, 5_000L);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during EOD retry backoff", exception);
        }
    }

    private FailureClass classify(String code, StepDefinition step) {
        // A drained=false response is the normal bounded-poll signal while the owned cutoff fence
        // remains active. It must reuse the same execution epoch/idempotency key.
        if ("PAYMENTS_NOT_DRAINED".equals(code) && "PAYMENTS_DRAIN".equals(step.code()))
            return FailureClass.TRANSIENT;
        if ("UPSTREAM_UNAVAILABLE".equals(code) || TRANSIENT_HTTP_CODES.contains(code))
            return FailureClass.TRANSIENT;
        if (BUSINESS_CODES.contains(code)) return FailureClass.BUSINESS;
        return FailureClass.PERMANENT;
    }
}

enum FailureClass { TRANSIENT, BUSINESS, PERMANENT }

record FailureDescriptor(String code, String message, Map<String, Object> details,
                         FailureClass classification, boolean retryable) {}
