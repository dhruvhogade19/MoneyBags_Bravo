package com.moneybags.eod.api;

import com.moneybags.eod.domain.EodDomain.BusinessDateState;
import com.moneybags.eod.domain.EodDomain.EodExceptionRecord;
import com.moneybags.eod.domain.EodDomain.EodRun;
import com.moneybags.eod.domain.EodDomain.EodStep;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class EodResponses {
    private EodResponses() {}

    public record BusinessDateResponse(LocalDate businessDate, String status, Instant cutoffAt,
                                       Instant openedAt, Instant closedAt, long version) {
        public static BusinessDateResponse from(BusinessDateState state) {
            return new BusinessDateResponse(state.businessDate(), state.status().name(), state.cutoffAt(),
                    state.openedAt(), state.closedAt(), state.version());
        }
    }

    public record StepResponse(String stepCode, int sequence, String providerService, String method, String path,
                               String status, String commandReference, int attemptCount, Instant startedAt,
                               Instant completedAt, String errorCode, String message, Map<String, Object> output) {
        static StepResponse from(EodStep step) {
            return new StepResponse(step.definition().name(), step.definition().sequence(), step.definition().service(),
                    step.definition().method(), step.definition().path(), step.status().name(), step.commandReference(),
                    step.attemptCount(), step.startedAt(), step.completedAt(), step.errorCode(), step.message(), step.output());
        }
    }

    public record ExceptionResponse(String exceptionId, String stepCode, String severity, String errorCode,
                                    Map<String, Object> details, String status, String resolution,
                                    String resolvedBy, Instant resolvedAt) {
        static ExceptionResponse from(EodExceptionRecord exception) {
            return new ExceptionResponse(exception.exceptionId(), exception.stepCode(), exception.severity(),
                    exception.errorCode(), exception.details(), exception.status().name(), exception.resolution(),
                    exception.resolvedBy(), exception.resolvedAt());
        }
    }

    public record EodRunResponse(String runId, LocalDate businessDate, String status, String startedBy,
                                 Instant startedAt, Instant completedAt, List<StepResponse> steps,
                                 List<ExceptionResponse> exceptions, long version) {
        public static EodRunResponse from(EodRun run) {
            return new EodRunResponse(run.runId(), run.businessDate(), run.status().name(), run.startedBy(),
                    run.startedAt(), run.completedAt(), run.steps().stream().map(StepResponse::from).toList(),
                    run.exceptions().stream().map(ExceptionResponse::from).toList(), run.version());
        }
    }
}
