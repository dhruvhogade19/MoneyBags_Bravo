package com.moneybags.payments.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moneybags.payments.dto.IntegrationDtos.AccountingLookupResponse;
import com.moneybags.payments.dto.IntegrationDtos.AccountingResponse;
import org.junit.jupiter.api.Test;

class AccountingResponseContractTest {
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void timeoutLookupReadsAccountingStatusField() throws Exception {
    AccountingLookupResponse response = mapper.readValue("""
        {"externalReference":"PAYMENT:PAY-1:ACCOUNTING","status":"POSTED",
         "journalNumber":"JRN-1","receivedAt":"2026-08-16T10:00:00Z",
         "completedAt":"2026-08-16T10:00:01Z"}
        """, AccountingLookupResponse.class);

    assertThat(response.status()).isEqualTo("POSTED");
    assertThat(response.journalNumber()).isEqualTo("JRN-1");
  }

  @Test
  void journalResponseReadsAccountingReversalField() throws Exception {
    AccountingResponse response = mapper.readValue("""
        {"journalNumber":"JRN-2","status":"POSTED",
         "reversesJournalNumber":"JRN-1","postedAt":"2026-08-16T10:00:01Z"}
        """, AccountingResponse.class);

    assertThat(response.reversesJournalNumber()).isEqualTo("JRN-1");
  }
}
