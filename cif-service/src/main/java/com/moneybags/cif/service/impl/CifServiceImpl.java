package com.moneybags.cif.service.impl;

import com.moneybags.cif.domain.enums.KycStatus;
import com.moneybags.cif.dto.request.CreateCifRequest;
import com.moneybags.cif.dto.request.KycVerificationRequest;
import com.moneybags.cif.dto.request.UpdateCifRequest;
import com.moneybags.cif.dto.request.UpdateKycStatusRequest;
import com.moneybags.cif.dto.response.CifResponse;
import com.moneybags.cif.dto.response.CreditCardDetailsResponse;
import com.moneybags.cif.dto.response.CustomerContactDetailsResponse;
import com.moneybags.cif.dto.response.DepositCreationDetailsResponse;
import com.moneybags.cif.entity.Cif;
import com.moneybags.cif.domain.enums.EmploymentType;
import com.moneybags.cif.exception.DuplicateResourceException;
import com.moneybags.cif.exception.InvalidCifRequestException;
import com.moneybags.cif.exception.ResourceNotFoundException;
import com.moneybags.cif.repository.CifRepository;
import com.moneybags.cif.service.CifService;
import com.moneybags.cif.domain.event.CifCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class CifServiceImpl implements CifService {

    private final CifRepository cifRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CifServiceImpl(
            CifRepository cifRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.cifRepository = cifRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public CifResponse createCif(CreateCifRequest request) {
        validateSalary(request.employmentType(), request.salary());
        validateUniqueFieldsForCreate(request);

        Cif cif = new Cif();
        copyCreateRequestToEntity(request, cif);

        Cif savedCif = cifRepository.save(cif);

        eventPublisher.publishEvent(
                new CifCreatedEvent(toKycVerificationRequest(savedCif))
        );

        return toCifResponse(savedCif);
    }

    @Override
    @Transactional(readOnly = true)
    public CifResponse getCifById(Long cifId) {
        return toCifResponse(findCifById(cifId));
    }

    @Override
    public CifResponse updateCif(Long cifId, UpdateCifRequest request) {
        Cif cif = findCifById(cifId);

        validateSalary(request.employmentType(), request.salary());
        validateUniqueFieldsForUpdate(cifId, request);

        copyUpdateRequestToEntity(request, cif);

        cif.setKycStatus(KycStatus.PENDING);

        Cif updatedCif = cifRepository.save(cif);

        eventPublisher.publishEvent(
                new CifCreatedEvent(toKycVerificationRequest(updatedCif))
        );

        return toCifResponse(updatedCif);
    }

    @Override
    public CifResponse updateKycStatus(
            Long cifId,
            UpdateKycStatusRequest request
    ) {
        Cif cif = findCifById(cifId);

        cif.setKycStatus(request.kycStatus());

        Cif updatedCif = cifRepository.save(cif);
        return toCifResponse(updatedCif);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditCardDetailsResponse getCreditCardDetails(Long cifId) {
        Cif cif = findCifById(cifId);

        return new CreditCardDetailsResponse(
                cif.getCifId(),
                cif.getAge(),
                cif.getEmploymentType(),
                cif.getSalary(),
                cif.getKycStatus()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DepositCreationDetailsResponse getDepositCreationDetails(Long cifId) {
        Cif cif = findCifById(cifId);

        return new DepositCreationDetailsResponse(
                cif.getCifId(),
                cif.getDob(),
                cif.getEmploymentType(),
                cif.getKycStatus()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerContactDetailsResponse getCustomerContactDetails(Long cifId) {
        Cif cif = findCifById(cifId);

        return new CustomerContactDetailsResponse(
                cif.getCifId(),
                cif.getFirstName(),
                cif.getLastName(),
                cif.getEmail(),
                cif.getNumber(),
                cif.getAddress()
        );
    }

    private Cif findCifById(Long cifId) {
        return cifRepository.findById(cifId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CIF not found with id: " + cifId
                ));
    }

    private void validateUniqueFieldsForCreate(CreateCifRequest request) {
        if (cifRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email is already registered");
        }

        if (cifRepository.existsByNumber(request.number())) {
            throw new DuplicateResourceException("Mobile number is already registered");
        }

        if (cifRepository.existsByPanNumber(request.panNumber())) {
            throw new DuplicateResourceException("PAN number is already registered");
        }

        if (cifRepository.existsByAadhaarNumber(request.aadhaarNumber())) {
            throw new DuplicateResourceException("Aadhaar number is already registered");
        }
    }

    private void validateUniqueFieldsForUpdate(
            Long cifId,
            UpdateCifRequest request
    ) {
        if (cifRepository.existsByEmailAndCifIdNot(request.email(), cifId)) {
            throw new DuplicateResourceException("Email is already registered");
        }

        if (cifRepository.existsByNumberAndCifIdNot(request.number(), cifId)) {
            throw new DuplicateResourceException("Mobile number is already registered");
        }

        if (cifRepository.existsByPanNumberAndCifIdNot(
                request.panNumber(), cifId)) {
            throw new DuplicateResourceException("PAN number is already registered");
        }

        if (cifRepository.existsByAadhaarNumberAndCifIdNot(
                request.aadhaarNumber(), cifId)) {
            throw new DuplicateResourceException(
                    "Aadhaar number is already registered"
            );
        }
    }

    private void validateSalary(
            EmploymentType employmentType,
            BigDecimal salary
    ) {
        if (employmentType == EmploymentType.STUDENT && salary != null) {
            throw new InvalidCifRequestException(
                    "Salary must be empty when employment type is STUDENT"
            );
        }

        if (employmentType != EmploymentType.STUDENT
                && (salary == null || salary.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new InvalidCifRequestException(
                    "Salary must be greater than zero for BUSINESS or SALARIED employment"
            );
        }
    }

    private void copyCreateRequestToEntity(
            CreateCifRequest request,
            Cif cif
    ) {
        cif.setFirstName(request.firstName());
        cif.setLastName(request.lastName());
        cif.setDob(request.dob());
        cif.setAge(request.age());
        cif.setEmail(request.email());
        cif.setNumber(request.number());
        cif.setAddress(request.address());
        cif.setEmploymentType(request.employmentType());
        cif.setSalary(request.salary());
        cif.setPanNumber(request.panNumber());
        cif.setAadhaarNumber(request.aadhaarNumber());
    }

    private void copyUpdateRequestToEntity(
            UpdateCifRequest request,
            Cif cif
    ) {
        cif.setFirstName(request.firstName());
        cif.setLastName(request.lastName());
        cif.setDob(request.dob());
        cif.setAge(request.age());
        cif.setEmail(request.email());
        cif.setNumber(request.number());
        cif.setAddress(request.address());
        cif.setEmploymentType(request.employmentType());
        cif.setSalary(request.salary());
        cif.setPanNumber(request.panNumber());
        cif.setAadhaarNumber(request.aadhaarNumber());
    }

    private KycVerificationRequest toKycVerificationRequest(Cif cif) {
        return new KycVerificationRequest(
                cif.getCifId(),
                cif.getFirstName(),
                cif.getLastName(),
                cif.getDob(),
                cif.getEmail(),
                cif.getNumber(),
                cif.getAddress(),
                cif.getEmploymentType(),
                cif.getSalary(),
                cif.getKycStatus(),
                cif.getPanNumber(),
                cif.getAadhaarNumber()
        );
    }

    private CifResponse toCifResponse(Cif cif) {
        return new CifResponse(
                cif.getCifId(),
                cif.getFirstName(),
                cif.getLastName(),
                cif.getDob(),
                cif.getAge(),
                cif.getEmail(),
                cif.getNumber(),
                cif.getAddress(),
                cif.getEmploymentType(),
                cif.getSalary(),
                cif.getKycStatus(),
                cif.getPanNumber(),
                cif.getAadhaarNumber(),
                cif.getCreatedAt(),
                cif.getUpdatedAt()
        );
    }
}


//This is the main business-logic class.
//It validates salary and duplicate customer details.
//It saves or updates CIF data through CifRepository.
//It sends the complete non-timestamp CIF data to KYC Service after a successful CIF creation.
//It receives and stores KYC status updates.
//It deliberately returns only the approved fields to Credit Card, Deposit Creation, and Notification/Statement services.
//The private methods keep repeated logic—such as mapping, validation, and finding a CIF—in one place.