package com.moneybags.cif.service;

import com.moneybags.cif.dto.request.CreateCifRequest;
import com.moneybags.cif.dto.request.UpdateCifRequest;
import com.moneybags.cif.dto.request.UpdateKycStatusRequest;
import com.moneybags.cif.dto.response.CifResponse;
import com.moneybags.cif.dto.response.CreditCardDetailsResponse;
import com.moneybags.cif.dto.response.CustomerContactDetailsResponse;
import com.moneybags.cif.dto.response.DepositCreationDetailsResponse;

public interface CifService {

    CifResponse createCif(CreateCifRequest request);

    CifResponse getCifById(Long cifId);

    CifResponse updateCif(Long cifId, UpdateCifRequest request);

    CifResponse updateKycStatus(Long cifId, UpdateKycStatusRequest request);

    CreditCardDetailsResponse getCreditCardDetails(Long cifId);

    DepositCreationDetailsResponse getDepositCreationDetails(Long cifId);

    CustomerContactDetailsResponse getCustomerContactDetails(Long cifId);
}