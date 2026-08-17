package com.moneybags.productmaster;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductMasterIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseCreatesReadableProductSpecificTablesAndSeedsSixProducts() {
        Integer deposits = jdbcTemplate.queryForObject("select count(*) from DEPOSIT_PRODUCT", Integer.class);
        Integer cards = jdbcTemplate.queryForObject("select count(*) from CREDIT_CARD_PRODUCT", Integer.class);
        assertThat(deposits).isEqualTo(4);
        assertThat(cards).isEqualTo(2);
    }

    @Test
    void returnsBravoShapedPlatinumProductWithFixedVersions() throws Exception {
        mockMvc.perform(get("/api/products/CC-PLAT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Platinum Credit Card"))
                .andExpect(jsonPath("$.category").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.subtype").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.interestRule.policyVersion").value("V1"))
                .andExpect(jsonPath("$.interestRule.annualInterestRate").value(42.0))
                .andExpect(jsonPath("$.creditCardRule.policyVersion").value("V1"))
                .andExpect(jsonPath("$.fees.length()").value(2))
                .andExpect(jsonPath("$.eligibilityRules[0].kycRequired").value(true))
                .andExpect(jsonPath("$.features[0].featureName").value("contactless"));
    }

    @Test
    void depositAndCreditCardConsumersCanValidateAndQuoteRates() throws Exception {
        mockMvc.perform(post("/api/products/SAV-REG-001/validate-account-opening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"openingAmount":2000,"age":30,
                                 "customerType":"INDIVIDUAL","kycCompleted":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.applicableInterestRule.annualInterestRate").value(3.5));

        mockMvc.perform(post("/api/products/CC-PLAT-001/validate-credit-card-application")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedCreditLimit":100000,"age":30,"monthlyIncome":30000,
                                 "customerType":"INDIVIDUAL","kycCompleted":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.applicableInterestRule.annualInterestRate").value(42.0));

        mockMvc.perform(get("/api/products/CC-PLAT-001/rate-quote")
                        .queryParam("quoteDate", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeredAnnualRate").value(42.0))
                .andExpect(jsonPath("$.policyVersion").value("V1"));
    }

    @Test
    void fixedDepositAndMinimalCreditCardCatalogueUseTheCompatibleProductContract() throws Exception {
        mockMvc.perform(get("/api/products/FD-REG-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtype").value("FIXED_DEPOSIT"))
                .andExpect(jsonPath("$.fixedDepositRule.defaultInterestPayoutFrequency").value("AT_MATURITY"))
                .andExpect(jsonPath("$.interestRateSlabs.length()").value(4))
                .andExpect(jsonPath("$.prematureClosureRule.allowed").value(true));

        mockMvc.perform(get("/api/products/SAV-REG-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixedDepositRule").doesNotExist())
                .andExpect(jsonPath("$.interestRateSlabs.length()").value(0));

        mockMvc.perform(post("/api/products/FD-REG-001/validate-account-opening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"openingAmount":100000,"tenureMonths":12,"tenureUnit":"MONTH",
                                 "customerCategory":"REGULAR","age":30,"customerType":"INDIVIDUAL","kycCompleted":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true));

        mockMvc.perform(get("/api/products/category/CREDIT_CARD/active/minimal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productCode").value("CC-GOLD-001"))
                .andExpect(jsonPath("$[0].interestRate").value(36.0))
                .andExpect(jsonPath("$[0].eligibility.minimumMonthlyIncome").value(15000.0))
                .andExpect(jsonPath("$[0].messages[0]").value("KYC verification is required"));

        mockMvc.perform(get("/api/products/CC-PLAT-001/minimal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("CC-PLAT-001"))
                .andExpect(jsonPath("$.interestRate").value(42.0))
                .andExpect(jsonPath("$.eligibility.minimumMonthlyIncome").value(25000.0))
                .andExpect(jsonPath("$.messages[0]").value("KYC verification is required"));

        mockMvc.perform(get("/api/products/FD-REG-001/minimal"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void versionedAndInternalDepositContractsResolveOneApplicableRateSlab() throws Exception {
        mockMvc.perform(get("/api/v1/products/FD-REG-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("FD-REG-001"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(post("/internal/v1/products/FD-REG-001/validate-account-opening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"openingAmount":100000,"currency":"INR","productVersion":1,
                                 "tenureMonths":12,"tenureUnit":"MONTH","interestPayoutFrequency":"AT_MATURITY",
                                 "customerCategory":"REGULAR","age":30,"customerType":"INDIVIDUAL",
                                 "kycVerified":true,"valueDate":"2026-08-14"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.productCode").value("FD-REG-001"))
                .andExpect(jsonPath("$.productVersion").value(1))
                .andExpect(jsonPath("$.appliedRuleVersion").value(1))
                .andExpect(jsonPath("$.subtype").value("FIXED_DEPOSIT"))
                .andExpect(jsonPath("$.applicableInterestRateSlab.slabCode")
                        .value("FD-12M-TO-24M-REGULAR"))
                .andExpect(jsonPath("$.applicableInterestRateSlab.annualInterestRate").value(6.75));

        mockMvc.perform(post("/internal/v1/products/FD-REG-001/validate-account-opening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"openingAmount":100000,"currency":"INR","productVersion":1,
                                 "tenureMonths":12,"tenureUnit":"MONTH","interestPayoutFrequency":"AT_MATURITY",
                                 "customerCategory":"SENIOR_CITIZEN","age":65,"customerType":"INDIVIDUAL",
                                 "kycVerified":true,"valueDate":"2026-08-14"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.applicableInterestRateSlab.slabCode")
                        .value("FD-12M-TO-24M-SENIOR"))
                .andExpect(jsonPath("$.applicableInterestRateSlab.annualInterestRate").value(7.25));
    }

    @Test
    void internalDepositContractRejectsCurrencyAndVersionMismatches() throws Exception {
        mockMvc.perform(post("/internal/v1/products/SAV-REG-001/validate-account-opening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"openingAmount":2000,"currency":"USD","productVersion":2,
                                 "age":30,"customerType":"INDIVIDUAL","kycVerified":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.validationMessages[0]").value("Product version does not match the requested version"))
                .andExpect(jsonPath("$.validationMessages[1]").value("Product currency does not match the requested currency"));
    }

    @Test
    void depositEligibilityExplainsAgeAndCustomerTypeFailuresPrecisely() throws Exception {
        mockMvc.perform(post("/internal/v1/products/SAV-REG-001/validate-account-opening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"openingAmount":2000,"currency":"INR","productVersion":1,
                                 "age":0,"monthlyIncome":100000,"customerType":"INDIVIDUAL",
                                 "customerCategory":"REGULAR","kycVerified":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.validationMessages[0]").value(
                        "Customer must be at least 18 years old; profile age is 0"));

        mockMvc.perform(post("/internal/v1/products/CUR-BIZ-001/validate-account-opening")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"openingAmount":10000,"currency":"INR","productVersion":1,
                                 "age":30,"monthlyIncome":100000,"customerType":"INDIVIDUAL",
                                 "customerCategory":"REGULAR","kycVerified":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.validationMessages[0]").value(
                        "Product is available to BUSINESS customers; profile customer type is INDIVIDUAL"));
    }

    @Test
    void treasuryBenchmarkCanBePublishedAndFetched() throws Exception {
        mockMvc.perform(post("/api/benchmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"benchmarkCode":"RBI_REPO","annualRate":6.5,
                                 "effectiveFrom":"2026-08-10","createdBy":"test"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.benchmarkCode").value("RBI_REPO"));

        mockMvc.perform(get("/api/benchmarks/RBI_REPO/effective")
                        .queryParam("effectiveOn", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualRate").value(6.5));
    }

    @Test
    void exposesSwaggerOpenApiForProductMasterTesting() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Moneybags Product Master API"))
                .andExpect(jsonPath("$.paths['/api/products/category/CREDIT_CARD/active/minimal']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{productCode}/minimal']").exists());
    }
}
