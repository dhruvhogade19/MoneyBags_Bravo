package com.moneybags.creditcard.controller;

import com.moneybags.creditcard.dto.CreditCardDtos.EodReadinessResponse;
import com.moneybags.creditcard.service.CreditCardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalCreditCardControllerTest {
    @Test
    void exposesTheExistingReadinessBusinessLogicForEodServiceCalls() {
        CreditCardService service = mock(CreditCardService.class);
        EodReadinessResponse expected = new EodReadinessResponse(true, 4, 0, 0, List.of());
        when(service.eod()).thenReturn(expected);

        EodReadinessResponse actual = new InternalCreditCardController(service).eodReadiness();

        assertThat(actual).isSameAs(expected);
        verify(service).eod();
    }
}
