package com.moneybags.deposit.entity;

import com.moneybags.deposit.domain.DomainTypes.HolderRole;
import com.moneybags.deposit.domain.DomainTypes.RecordStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCOUNT_HOLDER", uniqueConstraints =
        @UniqueConstraint(name = "UQ_HOLDER_ACCOUNT_CUSTOMER", columnNames = {"ACCOUNT_ID", "CUSTOMER_ID"}))
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountHolder {
    @Id
    @Column(name = "HOLDER_ID", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    private DepositAccount account;

    @Column(name = "CUSTOMER_ID", length = 36, nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "HOLDER_ROLE", length = 16, nullable = false)
    private HolderRole role;

    @Column(name = "AUTHORIZATION_TYPE", length = 30)
    private String authorizationType;

    @Column(name = "OWNERSHIP_PCT", precision = 5, scale = 2)
    private BigDecimal ownershipPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 16, nullable = false)
    private RecordStatus status;

    @Column(name = "ADDED_AT", nullable = false)
    private OffsetDateTime addedAt;

    @Column(name = "REMOVED_AT")
    private OffsetDateTime removedAt;

    public AccountHolder(String id, String customerId, HolderRole role, String authorizationType,
                         BigDecimal ownershipPercentage) {
        this.id = id;
        this.customerId = customerId;
        this.role = role;
        this.authorizationType = authorizationType;
        this.ownershipPercentage = ownershipPercentage;
        this.status = RecordStatus.ACTIVE;
        this.addedAt = OffsetDateTime.now();
    }
}
