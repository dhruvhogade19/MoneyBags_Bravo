package com.moneybags.kycservice.dto.response;

import com.moneybags.kycservice.enums.DocumentType;
import com.moneybags.kycservice.enums.VerificationStatus;

import java.time.OffsetDateTime;

public record KycDocumentResponse(

        Long documentId,

        Long kycId,

        DocumentType documentType,

        String originalFileName,

        String contentType,

        Long fileSizeBytes,

        VerificationStatus verificationStatus,

        String verificationRemarks,

        String verifiedBy,

        OffsetDateTime verifiedAt,

        OffsetDateTime uploadedAt

) {
}