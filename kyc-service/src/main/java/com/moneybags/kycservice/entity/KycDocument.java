package com.moneybags.kycservice.entity;

import com.moneybags.kycservice.enums.DocumentType;
import com.moneybags.kycservice.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(
        name = "kyc_document",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_kyc_document_type",
                        columnNames = {
                                "kyc_id",
                                "document_type"
                        }
                )
        }
)
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "kyc_id",
            nullable = false
    )
    private Kyc kyc;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(
            name = "document_type",
            nullable = false,
            length = 30
    )
    private DocumentType documentType;

    @Setter
    @Column(
            name = "original_file_name",
            nullable = false,
            length = 500
    )
    private String originalFileName;

    @Setter
    @Column(
            name = "content_type",
            nullable = false,
            length = 100
    )
    private String contentType;

    @Setter
    @Column(
            name = "file_size_bytes",
            nullable = false
    )
    private Long fileSizeBytes;

    @Setter
    @Lob
    @Column(
            name = "document_content",
            nullable = false
    )
    private byte[] documentContent;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(
            name = "verification_status",
            nullable = false,
            length = 20
    )
    private VerificationStatus verificationStatus;

    @Setter
    @Lob
    @Column(name = "verification_remarks")
    private String verificationRemarks;

    @Setter
    @Column(
            name = "verified_by",
            length = 100
    )
    private String verifiedBy;

    @Setter
    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Setter
    @Column(
            name = "uploaded_at",
            nullable = false
    )
    private OffsetDateTime uploadedAt;

    @Column(
            name = "created_at",
            nullable = false
    )
    private OffsetDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private OffsetDateTime updatedAt;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;

    @PrePersist
    void prePersist() {

        OffsetDateTime now = OffsetDateTime.now();

        if (verificationStatus == null) {
            verificationStatus = VerificationStatus.PENDING;
        }

        if (uploadedAt == null) {
            uploadedAt = now;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

}