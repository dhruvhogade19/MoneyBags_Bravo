package com.moneybags.deposit.fixeddeposit.service;

import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.fixeddeposit.calculation.FixedDepositInterestCalculator;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositRequests.QuoteRequest;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositResponses.QuoteResponse;
import com.moneybags.deposit.fixeddeposit.integration.FixedDepositProductGateway;
import com.moneybags.deposit.integration.BankingReferenceGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FixedDepositQuoteService {
    private final FixedDepositProductGateway products; private final BankingReferenceGateway customers;
    private final FixedDepositInterestCalculator calculator;
    public FixedDepositQuoteService(FixedDepositProductGateway products, BankingReferenceGateway customers,
                                    FixedDepositInterestCalculator calculator) {
        this.products=products; this.customers=customers; this.calculator=calculator;
    }
    public QuoteResponse quote(QuoteRequest r) {
        if (!customers.validateCustomerEligibility(r.customerId()).eligible())
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"CUSTOMER_NOT_ELIGIBLE","Customer or KYC is not eligible");
        var t=products.resolve(r.productCode(),r.productVersion(),r.principal(),r.currency(),r.tenureValue(),
                r.tenureUnit(),r.interestPayoutFrequency(),r.valueDate());
        var c=calculator.calculate(r.principal(),t.annualRate(),r.valueDate(),r.tenureValue(),r.tenureUnit(),t.compoundingFrequency());
        return new QuoteResponse(t.productCode(),t.productVersion(),t.productName(),t.rateSlabCode(),t.annualRate(),
                r.principal(),r.valueDate(),c.maturityDate(),c.interest(),c.maturityAmount(),t.calculationMethod(),
                t.compoundingFrequency(),t.dayCountConvention());
    }
}
