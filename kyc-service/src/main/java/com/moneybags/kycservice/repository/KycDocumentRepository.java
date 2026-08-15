package com.moneybags.kycservice.repository;

import com.moneybags.kycservice.entity.KycDocument;
import com.moneybags.kycservice.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycDocumentRepository
        extends JpaRepository<KycDocument, Long> {

    List<KycDocument> findAllByKycKycId(
            Long kycId
    );

    Optional<KycDocument>
    findByDocumentIdAndKycKycId(
            Long documentId,
            Long kycId
    );

    boolean existsByKycKycIdAndDocumentType(
            Long kycId,
            DocumentType documentType
    );

}