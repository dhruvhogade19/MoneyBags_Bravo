package com.moneybags.kycservice.service;

import com.moneybags.kycservice.dto.request.DocumentVerificationRequest;
import com.moneybags.kycservice.dto.response.KycDocumentResponse;
import com.moneybags.kycservice.entity.Kyc;
import com.moneybags.kycservice.entity.KycDocument;
import com.moneybags.kycservice.enums.DocumentType;
import com.moneybags.kycservice.enums.KycStatus;
import com.moneybags.kycservice.enums.VerificationStatus;
import com.moneybags.kycservice.exception.BadRequestException;
import com.moneybags.kycservice.exception.ResourceNotFoundException;
import com.moneybags.kycservice.mapper.KycMapper;
import com.moneybags.kycservice.repository.KycDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class KycDocumentService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );

    private static final long MAX_FILE_SIZE =
            10 * 1024 * 1024;

    private final KycDocumentRepository documentRepository;
    private final KycService kycService;
    private final KycMapper kycMapper;

    public KycDocumentService(
            KycDocumentRepository documentRepository,
            KycService kycService,
            KycMapper kycMapper
    ) {
        this.documentRepository = documentRepository;
        this.kycService = kycService;
        this.kycMapper = kycMapper;
    }

    @Transactional
    public List<KycDocumentResponse> uploadDocuments(
            Long kycId,
            List<DocumentType> documentTypes,
            List<MultipartFile> files
    ) {

        validateBatchRequest(
                documentTypes,
                files
        );

        Kyc kyc = kycService.findKyc(kycId);

        validateKycAllowsDocumentChanges(kyc);

        /*
         * Validate the complete request before we start
         * inserting documents.
         */
        for (int i = 0; i < files.size(); i++) {

            DocumentType documentType =
                    documentTypes.get(i);

            MultipartFile file =
                    files.get(i);

            validateFile(file);

            boolean alreadyExists =
                    documentRepository
                            .existsByKycKycIdAndDocumentType(
                                    kycId,
                                    documentType
                            );

            if (alreadyExists) {

                throw new BadRequestException(
                        "Document type "
                                + documentType
                                + " already exists for KYC "
                                + kycId
                );
            }
        }

        List<KycDocumentResponse> responses =
                new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {

            DocumentType documentType =
                    documentTypes.get(i);

            MultipartFile file =
                    files.get(i);

            KycDocument document =
                    new KycDocument();

            document.setKyc(kyc);
            document.setDocumentType(documentType);

            document.setOriginalFileName(
                    file.getOriginalFilename()
            );

            document.setContentType(
                    file.getContentType()
            );

            document.setFileSizeBytes(
                    file.getSize()
            );

            try {

                document.setDocumentContent(
                        file.getBytes()
                );

            } catch (IOException exception) {

                throw new BadRequestException(
                        "Unable to read uploaded document: "
                                + file.getOriginalFilename()
                );
            }

            document.setVerificationStatus(
                    VerificationStatus.PENDING
            );

            KycDocument savedDocument =
                    documentRepository.save(document);

            responses.add(
                    kycMapper.toDocumentResponse(
                            savedDocument
                    )
            );
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public List<KycDocumentResponse> getDocuments(
            Long kycId
    ) {

        kycService.findKyc(kycId);

        return documentRepository
                .findAllByKycKycId(kycId)
                .stream()
                .map(kycMapper::toDocumentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public KycDocument getDocument(
            Long kycId,
            Long documentId
    ) {

        return documentRepository
                .findByDocumentIdAndKycKycId(
                        documentId,
                        kycId
                )
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Document not found with id: "
                                        + documentId
                        )
                );
    }

    @Transactional
    public KycDocumentResponse verifyDocument(
            Long kycId,
            Long documentId,
            DocumentVerificationRequest request
    ) {

        KycDocument document =
                getDocument(
                        kycId,
                        documentId
                );

        Kyc kyc = document.getKyc();

        validateKycAllowsDocumentChanges(kyc);

        if (request.status()
                == VerificationStatus.PENDING) {

            throw new BadRequestException(
                    "Verification status cannot be PENDING"
            );
        }

        document.setVerificationStatus(
                request.status()
        );

        document.setVerificationRemarks(
                request.remarks()
        );

        document.setVerifiedBy(
                request.verifiedBy()
        );

        document.setVerifiedAt(
                OffsetDateTime.now()
        );

        if (request.status()
                == VerificationStatus.MISMATCH) {

            kyc.setKycStatus(
                    KycStatus.FLAGGED
            );

            kyc.setMismatchReason(
                    request.remarks()
            );
        }

        KycDocument savedDocument =
                documentRepository.save(document);

        return kycMapper.toDocumentResponse(
                savedDocument
        );
    }

    private void validateBatchRequest(
            List<DocumentType> documentTypes,
            List<MultipartFile> files
    ) {

        if (documentTypes == null
                || files == null) {

            throw new BadRequestException(
                    "documentTypes and files are required"
            );
        }

        if (documentTypes.isEmpty()
                || files.isEmpty()) {

            throw new BadRequestException(
                    "At least one document must be provided"
            );
        }

        if (documentTypes.size()
                != files.size()) {

            throw new BadRequestException(
                    "Each file must have a corresponding documentType"
            );
        }

        long uniqueDocumentTypes =
                documentTypes
                        .stream()
                        .distinct()
                        .count();

        if (uniqueDocumentTypes
                != documentTypes.size()) {

            throw new BadRequestException(
                    "Duplicate document types are not allowed in the same request"
            );
        }
    }

    private void validateFile(
            MultipartFile file
    ) {

        if (file == null || file.isEmpty()) {

            throw new BadRequestException(
                    "Document file cannot be empty"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {

            throw new BadRequestException(
                    "Document size cannot exceed 10 MB"
            );
        }

        String contentType =
                file.getContentType();

        if (contentType == null
                || !ALLOWED_CONTENT_TYPES
                .contains(contentType)) {

            throw new BadRequestException(
                    "Only PDF, PNG and JPEG documents are allowed"
            );
        }
    }

    private void validateKycAllowsDocumentChanges(
            Kyc kyc
    ) {

        if (kyc.getKycStatus()
                == KycStatus.APPROVED
                ||
                kyc.getKycStatus()
                        == KycStatus.REJECTED) {

            throw new BadRequestException(
                    "Documents cannot be changed after final KYC decision"
            );
        }
    }
}