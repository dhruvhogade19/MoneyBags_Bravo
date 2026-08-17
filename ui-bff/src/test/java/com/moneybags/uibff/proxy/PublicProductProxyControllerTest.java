package com.moneybags.uibff.proxy;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneybags.uibff.api.BffExceptionHandler;
import com.moneybags.uibff.http.UpstreamResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class PublicProductProxyControllerTest {
    private GatewayProxyClient gateway;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        gateway = mock(GatewayProxyClient.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PublicProductProxyController(gateway, new ObjectMapper()))
                .setControllerAdvice(new BffExceptionHandler())
                .build();
    }

    @Test
    void forcesActiveStatusAndFiltersUnexpectedInactiveListEntries() throws Exception {
        when(gateway.publicGet(eq("/api/products"), anyString(), any())).thenReturn(json("""
                {"content":[
                  {"productCode":"SAV-ACTIVE","status":"ACTIVE"},
                  {"productCode":"DRAFT-SECRET","status":"DRAFT"}
                ],"page":0,"size":20,"totalElements":2,"totalPages":1,"first":true,"last":true}
                """));

        mockMvc.perform(get("/api/public/products")
                        .queryParam("category", "DEPOSIT")
                        .queryParam("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].productCode").value("SAV-ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(content().string(not(containsString("DRAFT-SECRET"))));

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(gateway).publicGet(eq("/api/products"), query.capture(), any());
        org.assertj.core.api.Assertions.assertThat(query.getValue())
                .contains("category=DEPOSIT", "status=ACTIVE")
                .doesNotContain("DRAFT");
    }

    @Test
    void guessedDraftDetailReturnsGenericNotFoundWithoutLeakingItsPayload() throws Exception {
        when(gateway.publicGet(eq("/api/products/DRAFT-SECRET"), isNull(), any())).thenReturn(json("""
                {"productCode":"DRAFT-SECRET","productName":"Unreleased VIP Card","status":"DRAFT"}
                """));

        mockMvc.perform(get("/api/public/products/DRAFT-SECRET"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found"))
                .andExpect(content().string(not(containsString("Unreleased VIP Card"))));
    }

    @Test
    void activeDetailRemainsPublic() throws Exception {
        when(gateway.publicGet(eq("/api/products/SAV-ACTIVE"), isNull(), any())).thenReturn(json("""
                {"productCode":"SAV-ACTIVE","productName":"Everyday Savings","status":"ACTIVE"}
                """));

        mockMvc.perform(get("/api/public/products/SAV-ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Everyday Savings"));
    }

    private static UpstreamResponse json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        return new UpstreamResponse(HttpStatus.OK, headers, body.getBytes(StandardCharsets.UTF_8));
    }
}
