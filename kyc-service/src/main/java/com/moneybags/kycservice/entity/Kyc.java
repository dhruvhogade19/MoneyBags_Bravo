package com.moneybags.kycservice.entity;

import com.moneybags.kycservice.enums.CifSyncStatus;
import com.moneybags.kycservice.enums.EmploymentType;
import com.moneybags.kycservice.enums.KycDecision;
import com.moneybags.kycservice.enums.KycStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "kyc")
public class Kyc {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kyc_id")
    private Long kycId;

    @Setter
    @Column(
            name = "cif_id",
            nullable = false
    )
    private Long cifId;

    @Setter
    @Column(
            name = "customer_name",
            nullable = false,
            length = 200
    )
    private String customerName;

    @Setter
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Setter
    @Column(
            name = "mobile_number",
            length = 20
    )
    private String mobileNumber;

    @Setter
    @Column(
            name = "email",
            length = 255
    )
    private String email;

    @Setter
    @Column(
            name = "pan_number",
            length = 20
    )
    private String panNumber;

    @Setter
    @Column(
            name = "aadhaar_number",
            length = 20
    )
    private String aadhaarNumber;

    @Setter
    @Column(
            name = "address_line1",
            length = 500
    )
    private String addressLine1;

    @Setter
    @Column(
            name = "address_line2",
            length = 500
    )
    private String addressLine2;

    @Setter
    @Column(
            name = "city",
            length = 100
    )
    private String city;

    @Setter
    @Column(
            name = "state",
            length = 100
    )
    private String state;

    @Setter
    @Column(
            name = "postal_code",
            length = 20
    )
    private String postalCode;

    @Setter
    @Column(
            name = "country",
            length = 100
    )
    private String country;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(
            name = "employment_type",
            length = 30
    )
    private EmploymentType employmentType;

    @Setter
    @Column(
            name = "salary",
            precision = 15,
            scale = 2
    )
    private BigDecimal salary;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(
            name = "kyc_status",
            nullable = false,
            length = 20
    )
    private KycStatus kycStatus;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(
            name = "decision",
            length = 20
    )
    private KycDecision decision;

    @Setter
    @Lob
    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Setter
    @Lob
    @Column(name = "mismatch_reason")
    private String mismatchReason;

    @Setter
    @Column(
            name = "initiated_by",
            nullable = false,
            length = 100
    )
    private String initiatedBy;

    @Setter
    @Column(
            name = "reviewed_by",
            length = 100
    )
    private String reviewedBy;

    @Setter
    @Column(
            name = "initiated_at",
            nullable = false
    )
    private OffsetDateTime initiatedAt;

    @Setter
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(
            name = "cif_sync_status",
            nullable = false,
            length = 20
    )
    private CifSyncStatus cifSyncStatus;

    @Setter
    @Column(
            name = "sync_retry_count",
            nullable = false
    )
    private Integer syncRetryCount;

    @Setter
    @Column(name = "last_sync_attempt_at")
    private OffsetDateTime lastSyncAttemptAt;

    @Setter
    @Lob
    @Column(name = "last_sync_error")
    private String lastSyncError;

    @Setter
    @Column(name = "cif_synced_at")
    private OffsetDateTime cifSyncedAt;

    @Column(
            name = "created_at",
            nullable = false
    )
    private OffsetDateTime createdAt;

    @Setter
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

        if (kycStatus == null) {
            kycStatus = KycStatus.PENDING;
        }

        if (cifSyncStatus == null) {
            cifSyncStatus = CifSyncStatus.PENDING;
        }

        if (syncRetryCount == null) {
            syncRetryCount = 0;
        }

        if (initiatedAt == null) {
            initiatedAt = now;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

}
