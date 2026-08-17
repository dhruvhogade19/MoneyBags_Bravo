package com.moneybags.deposit.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "IDEMPOTENCY_RECORD", uniqueConstraints =
        @UniqueConstraint(name = "UQ_IDEMPOTENCY_SCOPE_KEY", columnNames = {"IDEMPOTENCY_SCOPE", "KEY_HASH"}))
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {
    @Id @Column(name = "RECORD_ID", length = 36)
    private String id;
    @Column(name = "IDEMPOTENCY_SCOPE", length = 80, nullable = false)
    private String scope;
    @Column(name = "KEY_HASH", length = 64, nullable = false)
    private String keyHash;
    @Column(name = "REQUEST_HASH", length = 64, nullable = false)
    private String requestHash;
    @Column(name = "PROCESSING_STATUS", length = 16, nullable = false)
    private String processingStatus;
    @Column(name = "RESOURCE_ID", length = 36)
    private String resourceId;
    @Column(name = "HTTP_STATUS", precision = 3)
    private Integer httpStatus;
    @Lob @Column(name = "RESPONSE_BODY")
    private String responseBody;
    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "EXPIRES_AT", nullable = false)
    private OffsetDateTime expiresAt;

    public IdempotencyRecord(String id, String scope, String keyHash, String requestHash, OffsetDateTime expiresAt) {
        this.id = id;
        this.scope = scope;
        this.keyHash = keyHash;
        this.requestHash = requestHash;
        this.processingStatus = "PROCESSING";
        this.createdAt = OffsetDateTime.now();
        this.expiresAt = expiresAt;
    }
}
