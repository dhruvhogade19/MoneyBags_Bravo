package com.moneybags.eod.service;

import com.moneybags.eod.adapter.memory.InMemoryStores;
import com.moneybags.eod.api.EodRequests.StartEodRunRequest;
import com.moneybags.eod.config.EodProperties;
import com.moneybags.eod.domain.EodDomain.StepDefinition;
import com.moneybags.eod.port.PeerOperations;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EodPeerContractTest {
    @Test
    void casaAndFixedDepositRequestsMatchPeerContracts() {
        LocalDate businessDate = LocalDate.of(2026, 8, 14);
        Clock clock = Clock.fixed(Instant.parse("2026-08-14T16:00:00Z"), ZoneOffset.UTC);
        EodProperties properties = new EodProperties();
        properties.setInitialBusinessDate(businessDate);
        CapturingPeerOperations peers = new CapturingPeerOperations();
        EodOrchestrationService service = new EodOrchestrationService(
                new InMemoryStores.Runs(), new InMemoryStores.BusinessDates(properties, clock),
                new InMemoryStores.Idempotency(), peers, clock);

        service.start("eod-peer-contract", new StartEodRunRequest(businessDate, "contract.test"));

        PeerOperations.Request casaReadiness = peers.requests.get(StepDefinition.DEPOSIT_READINESS);
        assertThat(casaReadiness.body()).isEmpty();

        PeerOperations.Request casaAccrual = peers.requests.get(StepDefinition.DEPOSIT_ACCRUALS);
        assertThat(casaAccrual.body()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "eodRunId", casaAccrual.eodRunId(), "commandReference", "DEP-ACCRUAL-20260814-V1",
                "businessDate", businessDate, "currency", "INR"));
        assertThat(casaAccrual.headers()).isEmpty();

        PeerOperations.Request fdAccrual = peers.requests.get(StepDefinition.FD_INTEREST_ACCRUAL);
        assertThat(fdAccrual.body()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "eodRunId", fdAccrual.eodRunId(), "businessDate", businessDate,
                "commandReference", "FD-ACCRUAL-20260814-V1"));
        assertThat(fdAccrual.headers()).containsEntry("Idempotency-Key", "FD-ACCRUAL-20260814-V1");

        PeerOperations.Request fdMaturity = peers.requests.get(StepDefinition.FD_MATURITY_PROCESSING);
        assertThat(fdMaturity.body()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "eodRunId", fdMaturity.eodRunId(), "businessDate", businessDate,
                "commandReference", "FD-MATURITY-20260814-V1"));
        assertThat(fdMaturity.headers()).containsEntry("Idempotency-Key", "FD-MATURITY-20260814-V1");

        PeerOperations.Request fdReadiness = peers.requests.get(StepDefinition.FD_READINESS_CHECK);
        assertThat(fdReadiness.body()).isEmpty();
        assertThat(fdReadiness.headers()).isEmpty();
    }

    private static final class CapturingPeerOperations implements PeerOperations {
        private final Map<StepDefinition, Request> requests = new EnumMap<>(StepDefinition.class);
        public Result execute(StepDefinition step, Request request) {
            requests.put(step, request);
            return new Result(true, "OK", "captured", Map.of());
        }
        public Result openAccountingPeriod(LocalDate businessDate, String requestedBy, String commandReference) {
            return new Result(true, "OK", "captured", Map.of());
        }
    }
}
