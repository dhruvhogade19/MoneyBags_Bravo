package com.moneybags.accounting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "IDEMPOTENCY_RECORD", uniqueConstraints = @UniqueConstraint(columnNames = {"SCOPE", "KEY_HASH"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {
    @Id @Column(name = "RECORD_ID", length = 36) private String id;
    @Column(name = "SCOPE", length = 100, nullable = false) private String scope;
    @Column(name = "KEY_HASH", length = 64, nullable = false) private String keyHash;
    @Column(name = "REQUEST_HASH", length = 64, nullable = false) private String requestHash;
    @Column(name = "RESOURCE_ID", length = 100) private String resourceId;
    @Column(name = "HTTP_STATUS", nullable = false) private int httpStatus;
    @Lob @Column(name = "RESPONSE_BODY", nullable = false) private String responseBody;
    @Column(name = "CREATED_AT", nullable = false) private OffsetDateTime createdAt;

    public IdempotencyRecord(String id, String scope, String keyHash, String requestHash, String resourceId,
                             int httpStatus, String responseBody) {
        this.id = id; this.scope = scope; this.keyHash = keyHash; this.requestHash = requestHash;
        this.resourceId = resourceId; this.httpStatus = httpStatus; this.responseBody = responseBody;
        this.createdAt = OffsetDateTime.now();
    }
}
