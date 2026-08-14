package com.moneybags.accounting.entity;

import com.moneybags.accounting.domain.DomainTypes.PostingStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "POSTING_REQUEST")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostingRequest {
    @Id @Column(name = "POSTING_REQUEST_ID", length = 36) private String id;
    @Column(name = "EXTERNAL_REFERENCE", length = 160, nullable = false, unique = true) private String externalReference;
    @Column(name = "IDEMPOTENCY_KEY_HASH", length = 64, nullable = false, unique = true) private String idempotencyKeyHash;
    @Column(name = "REQUEST_HASH", length = 64, nullable = false) private String requestHash;
    @Column(name = "SOURCE_SERVICE", length = 80, nullable = false) private String sourceService;
    @Column(name = "EVENT_TYPE", length = 60, nullable = false) private String eventType;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 20, nullable = false) private PostingStatus status;
    @Column(name = "JOURNAL_NUMBER", length = 60) private String journalNumber;
    @Column(name = "RECEIVED_AT", nullable = false) private OffsetDateTime receivedAt;
    @Column(name = "COMPLETED_AT") private OffsetDateTime completedAt;

    public PostingRequest(String id, String externalReference, String idempotencyKeyHash, String requestHash,
                          String sourceService, String eventType) {
        this.id = id; this.externalReference = externalReference; this.idempotencyKeyHash = idempotencyKeyHash;
        this.requestHash = requestHash;
        this.sourceService = sourceService; this.eventType = eventType; this.status = PostingStatus.RECEIVED;
        this.receivedAt = OffsetDateTime.now();
    }

    public void posted(String journalNumber) {
        this.status = PostingStatus.POSTED; this.journalNumber = journalNumber; this.completedAt = OffsetDateTime.now();
    }
}
