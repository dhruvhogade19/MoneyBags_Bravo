package com.moneybags.deposit.entity;

import com.moneybags.deposit.domain.DomainTypes.AccountStatus;
import com.moneybags.deposit.domain.DomainTypes.OperatingInstruction;
import com.moneybags.deposit.domain.DomainTypes.ProductSubtype;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "DEPOSIT_ACCOUNT")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepositAccount {
    @Id
    @Column(name = "ACCOUNT_ID", length = 36, nullable = false)
    private String id;

    @Column(name = "ACCOUNT_NUMBER", length = 34, nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "PRODUCT_ID", length = 36, nullable = false)
    private String productId;

    @Column(name = "PRODUCT_VERSION", precision = 10, nullable = false)
    private Long productVersion;

    @Column(name = "PRODUCT_NAME_SNAPSHOT", length = 120, nullable = false)
    private String productNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRODUCT_SUBTYPE", length = 30, nullable = false)
    private ProductSubtype productSubtype;

    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false)
    private String currencyCode;

    @Column(name = "SERVICING_BRANCH_ID", length = 36, nullable = false)
    private String servicingBranchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "OPERATING_INSTRUCTION", length = 30, nullable = false)
    private OperatingInstruction operatingInstruction;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 24, nullable = false)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "PREVIOUS_SERVICEABLE_STATUS", length = 24)
    private AccountStatus previousServiceableStatus;

    @Column(name = "EXTERNAL_REFERENCE", length = 64)
    private String externalReference;

    @Column(name = "OPENED_AT")
    private OffsetDateTime openedAt;

    @Column(name = "CLOSED_AT")
    private OffsetDateTime closedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false)
    private long version;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "CREATED_BY", length = 100, nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "UPDATED_AT", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "UPDATED_BY", length = 100, nullable = false)
    private String updatedBy;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("addedAt ASC")
    private List<AccountHolder> holders = new ArrayList<>();

    @OneToOne(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AccountBalance balance;

    public DepositAccount(String id, String accountNumber, String productId, Long productVersion,
                          String productNameSnapshot, String currencyCode, String servicingBranchId,
                          OperatingInstruction operatingInstruction, String externalReference, String actor) {
        this(id, accountNumber, productId, productVersion, productNameSnapshot, ProductSubtype.SAVINGS,
                currencyCode, servicingBranchId, operatingInstruction, externalReference, actor);
    }

    public DepositAccount(String id, String accountNumber, String productId, Long productVersion,
                          String productNameSnapshot, ProductSubtype productSubtype, String currencyCode,
                          String servicingBranchId, OperatingInstruction operatingInstruction,
                          String externalReference, String actor) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.productId = productId;
        this.productVersion = productVersion;
        this.productNameSnapshot = productNameSnapshot;
        this.productSubtype = productSubtype;
        this.currencyCode = currencyCode;
        this.servicingBranchId = servicingBranchId;
        this.operatingInstruction = operatingInstruction;
        this.externalReference = externalReference;
        this.status = AccountStatus.PENDING_ACTIVATION;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void addHolder(AccountHolder holder) {
        holders.add(holder);
        holder.setAccount(this);
    }

    public void setBalanceProjection(AccountBalance balance) {
        this.balance = balance;
        balance.setAccount(this);
    }
}
