package com.moneybags.cif.integration;

import com.moneybags.cif.domain.event.CifCreatedEvent;
import com.moneybags.cif.exception.KycServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KycInitiationListener {

    private static final Logger log =
            LoggerFactory.getLogger(KycInitiationListener.class);

    private final KycServiceClient kycServiceClient;

    public KycInitiationListener(KycServiceClient kycServiceClient) {
        this.kycServiceClient = kycServiceClient;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void initiateKyc(CifCreatedEvent event) {
        try {
            kycServiceClient.initiateKycVerification(
                    event.kycVerificationRequest()
            );
        } catch (KycServiceUnavailableException exception) {
            log.error(
                    "CIF was created, but KYC initiation could not be sent. cifId={}",
                    event.kycVerificationRequest().cifId(),
                    exception
            );
        }
    }
}