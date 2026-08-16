package com.moneybags.payments.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.payments.dto.IntegrationDtos.AccountingInstrument;
import org.junit.jupiter.api.Test;

class AccountingInstrumentContractTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void merchantReferenceUsesTheFieldExpectedByAccountingService() throws Exception {
    AccountingInstrument merchant = new AccountingInstrument("MERCHANT", null, "MERCHANT-001");

    String json = mapper.writeValueAsString(merchant);

    assertThat(json).contains("\"instrumentType\":\"MERCHANT\"")
        .contains("\"merchantId\":\"MERCHANT-001\"")
        .contains("\"accountId\":null");
  }

  @Test
  void accountReferenceKeepsTheExistingTwoArgumentContract() throws Exception {
    AccountingInstrument account = new AccountingInstrument("DEPOSIT_ACCOUNT", "dep-001");

    String json = mapper.writeValueAsString(account);

    assertThat(json).contains("\"accountId\":\"dep-001\"")
        .contains("\"merchantId\":null");
  }
}
