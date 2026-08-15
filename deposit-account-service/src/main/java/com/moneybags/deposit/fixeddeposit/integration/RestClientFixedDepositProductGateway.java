package com.moneybags.deposit.fixeddeposit.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.integration.ProductMasterClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@ConditionalOnProperty(name="moneybags.deposit.stub-upstream-clients", havingValue="false")
public class RestClientFixedDepositProductGateway implements FixedDepositProductGateway {
    private final ProductMasterClient client; private final ObjectMapper mapper;
    public RestClientFixedDepositProductGateway(ProductMasterClient client, ObjectMapper mapper) { this.client=client; this.mapper=mapper; }

    @Override @CircuitBreaker(name="referenceServices")
    public ProductTerms resolve(String code, Long version, BigDecimal principal, String currency, int tenure,
                                TenureUnit unit, InterestPayoutFrequency payout, LocalDate valueDate) {
        try {
            var p=client.getProduct(code);
            if (!p.active() || !"DEPOSIT".equalsIgnoreCase(p.category()) || !"FIXED_DEPOSIT".equalsIgnoreCase(p.resolvedSubtype()))
                fail("PRODUCT_NOT_ACTIVE_FIXED_DEPOSIT", "Product is not an active fixed deposit");
            if (!version.equals(p.version()) || !currency.equals(p.currencyCode())) fail("PRODUCT_VERSION_OR_CURRENCY_MISMATCH", "Product version or currency does not match");
            var a=p.amountRule();
            if (a==null || below(principal,a.minimumAmount()) || above(principal,a.maximumAmount()) ||
                    unit!=TenureUnit.MONTH || below(tenure,a.minimumTenureMonths()) || above(tenure,a.maximumTenureMonths()))
                fail("FD_AMOUNT_OR_TENURE_INVALID", "Principal or tenure is outside product rules");
            List<ProductMasterClient.InterestRateSlab> matches=(p.interestRateSlabs()==null?List.<ProductMasterClient.InterestRateSlab>of():p.interestRateSlabs()).stream()
                    .filter(s -> !Boolean.FALSE.equals(s.active()) && unit.name().equalsIgnoreCase(s.tenureUnit()))
                    .filter(s -> !below(tenure,s.minimumTenure()) && !above(tenure,s.maximumTenure()))
                    .filter(s -> !below(principal,s.minimumAmount()) && !above(principal,s.maximumAmount()))
                    .filter(s -> (s.effectiveFrom()==null || !valueDate.isBefore(s.effectiveFrom())) && (s.effectiveTo()==null || !valueDate.isAfter(s.effectiveTo())))
                    .toList();
            BigDecimal rate;
            String slab;
            if (matches.size()==1) { rate=matches.getFirst().annualInterestRate(); slab=matches.getFirst().slabCode(); }
            else if (matches.isEmpty() && p.interestRule()!=null && p.interestRule().annualInterestRate()!=null) { rate=p.interestRule().annualInterestRate(); slab="DEFAULT"; }
            else throw failure("FD_RATE_CONFIGURATION_INVALID", "Exactly one applicable interest-rate slab is required");
            var ir=p.interestRule(); var fr=p.fixedDepositRule();
            return new ProductTerms(code,version,p.productName(),slab,ir==null?null:ir.policyVersion(),rate,
                    ir==null||ir.interestCalculationMethod()==null?"COMPOUND_INTEREST":ir.interestCalculationMethod(),
                    CompoundingFrequency.valueOf(fr==null||fr.compoundingFrequency()==null?"QUARTERLY":fr.compoundingFrequency()), payout,
                    DayCountConvention.valueOf(fr==null||fr.dayCountConvention()==null?"ACTUAL_365":fr.dayCountConvention()), mapper.writeValueAsString(p));
        } catch (RestClientException ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PRODUCT_MASTER_UNAVAILABLE","Product Master is unavailable"); }
        catch (JsonProcessingException ex) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"PRODUCT_SNAPSHOT_FAILED","Could not snapshot product rules"); }
    }
    private boolean below(BigDecimal v, BigDecimal min){return min!=null&&v.compareTo(min)<0;} private boolean above(BigDecimal v, BigDecimal max){return max!=null&&v.compareTo(max)>0;}
    private boolean below(int v,Integer min){return min!=null&&v<min;} private boolean above(int v,Integer max){return max!=null&&v>max;}
    private void fail(String code,String message){throw failure(code,message);}
    private ApiException failure(String code,String message){return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,code,message);}
}
