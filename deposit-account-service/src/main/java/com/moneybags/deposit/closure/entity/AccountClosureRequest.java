package com.moneybags.deposit.closure.entity;

import com.moneybags.deposit.domain.DomainTypes.ClosureRequestStatus;
import com.moneybags.deposit.domain.DomainTypes.ClosureType;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name="ACCOUNT_CLOSURE_REQUEST", indexes={@Index(name="IX_CLOSURE_ACCOUNT_TIME",columnList="ACCOUNT_ID, CREATED_AT")})
@Getter @Setter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AccountClosureRequest {
    @Id @Column(name="CLOSURE_REQUEST_ID",length=36) private String id;
    @Column(name="ACCOUNT_ID",length=36,nullable=false) private String accountId;
    @Enumerated(EnumType.STRING) @Column(name="CLOSURE_TYPE",length=32,nullable=false) private ClosureType closureType;
    @Column(name="REQUESTED_BY",length=100,nullable=false) private String requestedBy;
    @Column(name="REQUESTED_CHANNEL",length=30,nullable=false) private String requestedChannel;
    @Column(name="REQUESTED_AT",nullable=false) private OffsetDateTime requestedAt;
    @Column(name="REQUESTED_DATE",nullable=false) private LocalDate requestedDate;
    @Column(name="REASON_CODE",length=40,nullable=false) private String reasonCode;
    @Column(name="REASON_TEXT",length=500) private String reasonText;
    @Column(name="DESTINATION_ACCOUNT_ID",length=36) private String destinationAccountId;
    @Enumerated(EnumType.STRING) @Column(name="STATUS",length=30,nullable=false) private ClosureRequestStatus status;
    @Column(name="REJECTION_CODE",length=80) private String rejectionCode;
    @Column(name="REJECTION_DETAILS",length=1000) private String rejectionDetails;
    @Column(name="POLICY_VERSION",length=30,nullable=false) private String policyVersion;
    @Column(name="CORRELATION_ID",length=64,nullable=false) private String correlationId;
    @Version @Column(name="VERSION_NO",nullable=false) private long version;
    @Column(name="CREATED_AT",nullable=false,updatable=false) private OffsetDateTime createdAt;
    @Column(name="UPDATED_AT",nullable=false) private OffsetDateTime updatedAt;
    @Column(name="COMPLETED_AT") private OffsetDateTime completedAt;

    public AccountClosureRequest(String id,String accountId,ClosureType type,String actor,String channel,
        LocalDate requestedDate,String reasonCode,String reasonText,String destination,String policy,String correlationId){
        this.id=id;this.accountId=accountId;this.closureType=type;this.requestedBy=actor;this.requestedChannel=channel;
        this.requestedAt=OffsetDateTime.now();this.requestedDate=requestedDate;this.reasonCode=reasonCode;
        this.reasonText=reasonText;this.destinationAccountId=destination;this.status=ClosureRequestStatus.REQUESTED;
        this.policyVersion=policy;this.correlationId=correlationId;this.createdAt=requestedAt;this.updatedAt=requestedAt;
    }
    public void transition(ClosureRequestStatus target){this.status=target;this.updatedAt=OffsetDateTime.now();if(target==ClosureRequestStatus.CLOSED||target==ClosureRequestStatus.CANCELLED)this.completedAt=updatedAt;}
}
