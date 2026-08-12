package com.moneybags.deposit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.deposit.domain.DomainTypes.AccountStatus;
import com.moneybags.deposit.entity.AccountBalance;
import com.moneybags.deposit.entity.AccountHolder;
import com.moneybags.deposit.entity.DepositAccount;
import com.moneybags.deposit.repository.DepositAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static com.moneybags.deposit.domain.DomainTypes.HolderRole.PRIMARY;
import static com.moneybags.deposit.domain.DomainTypes.OperatingInstruction.SINGLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DepositPaymentOperationControllerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DepositAccountRepository accountRepository;

    private String sourceId;
    private String targetId;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID().toString();
        targetId = UUID.randomUUID().toString();
        accountRepository.save(account(sourceId, "CIF-PAYER", new BigDecimal("10000.0000")));
        accountRepository.save(account(targetId, "CIF-PAYEE", BigDecimal.ZERO.setScale(4)));
    }

    @Test
    void bookTransferReservationAndSettlementAreAtomicAndIdempotent() throws Exception {
        String paymentId = "PAY-BOOK-" + UUID.randomUUID();
        MvcResult reserved = mockMvc.perform(post("/api/v1/internal/deposit-payment-operations/book-transfers/reservations")
                        .header("Idempotency-Key", paymentId + "-reserve")
                        .header("X-Correlation-Id", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + paymentId + "\",\"requestorCustomerId\":\"CIF-PAYER\"," +
                                "\"sourceAccountId\":\"" + sourceId + "\",\"targetAccountId\":\"" + targetId + "\"," +
                                "\"amount\":2000,\"currencyCode\":\"INR\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
        String reservationId = json(reserved).path("reservationId").asText();

        mockMvc.perform(post("/api/v1/internal/deposit-payment-operations/book-transfers/{paymentId}/settle", paymentId)
                        .header("Idempotency-Key", paymentId + "-settle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\":\"" + reservationId + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.transactionIds.length()").value(3));

        DepositAccount source = accountRepository.findDetailedById(sourceId).orElseThrow();
        DepositAccount target = accountRepository.findDetailedById(targetId).orElseThrow();
        assertThat(source.getBalance().getLedgerBalance()).isEqualByComparingTo("8000");
        assertThat(source.getBalance().getAvailableBalance()).isEqualByComparingTo("8000");
        assertThat(target.getBalance().getLedgerBalance()).isEqualByComparingTo("2000");
    }

    @Test
    void cardRepaymentCanBeReservedAndReleasedSafely() throws Exception {
        String paymentId = "PAY-CARD-" + UUID.randomUUID();
        MvcResult reserved = mockMvc.perform(post("/api/v1/internal/deposit-payment-operations/credit-card-repayments/reservations")
                        .header("Idempotency-Key", paymentId + "-reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + paymentId + "\",\"requestorCustomerId\":\"CIF-PAYER\"," +
                                "\"sourceAccountId\":\"" + sourceId + "\",\"creditCardAccountId\":\"CC-1001\"," +
                                "\"amount\":1500,\"currencyCode\":\"INR\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
        String reservationId = json(reserved).path("reservationId").asText();

        mockMvc.perform(post("/api/v1/internal/deposit-payment-operations/reservations/{id}/release", reservationId)
                        .header("Idempotency-Key", paymentId + "-release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + paymentId + "\",\"reasonCode\":\"ACCOUNTING_REJECTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
        mockMvc.perform(get("/api/v1/internal/deposit-payment-operations/{paymentId}", paymentId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));

        DepositAccount source = accountRepository.findDetailedById(sourceId).orElseThrow();
        assertThat(source.getBalance().getLedgerBalance()).isEqualByComparingTo("10000");
        assertThat(source.getBalance().getAvailableBalance()).isEqualByComparingTo("10000");
    }

    private DepositAccount account(String id, String customerId, BigDecimal balance) {
        DepositAccount account = new DepositAccount(id, "MB" + id.replace("-", "").substring(0, 12),
                "SAV-001", 1L, "Savings", "INR", "BR-001", SINGLE, null, "test");
        account.setStatus(AccountStatus.ACTIVE);
        account.addHolder(new AccountHolder(UUID.randomUUID().toString(), customerId, PRIMARY, "SINGLE", null));
        AccountBalance projection = AccountBalance.initial("INR", UUID.randomUUID().toString());
        projection.setLedgerBalance(balance);
        projection.setAvailableBalance(balance);
        account.setBalanceProjection(projection);
        return account;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
