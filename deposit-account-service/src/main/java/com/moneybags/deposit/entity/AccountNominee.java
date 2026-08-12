package com.moneybags.deposit.entity;

import com.moneybags.deposit.domain.DomainTypes.RecordStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCOUNT_NOMINEE")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountNominee {
    @Id @Column(name = "NOMINEE_ID", length = 36)
    private String id;
    @Column(name = "ACCOUNT_ID", length = 36, nullable = false)
    private String accountId;
    @Column(name = "CUSTOMER_REFERENCE", length = 36)
    private String customerReference;
    @Lob @Column(name = "NOMINEE_NAME_CIPHER")
    private String encryptedName;
    @Column(name = "RELATIONSHIP_CODE", length = 30, nullable = false)
    private String relationshipCode;
    @Column(name = "ALLOCATION_PCT", precision = 5, scale = 2, nullable = false)
    private BigDecimal allocationPercentage;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 16, nullable = false)
    private RecordStatus status;
    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    public AccountNominee(String id, String accountId, String customerReference, String encryptedName,
                          String relationshipCode, BigDecimal allocationPercentage) {
        this.id = id;
        this.accountId = accountId;
        this.customerReference = customerReference;
        this.encryptedName = encryptedName;
        this.relationshipCode = relationshipCode;
        this.allocationPercentage = allocationPercentage;
        this.status = RecordStatus.ACTIVE;
        this.createdAt = OffsetDateTime.now();
    }
}
