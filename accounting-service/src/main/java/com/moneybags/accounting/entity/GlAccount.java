package com.moneybags.accounting.entity;

import com.moneybags.accounting.domain.DomainTypes.GlAccountType;
import com.moneybags.accounting.domain.DomainTypes.NormalBalance;
import com.moneybags.accounting.domain.DomainTypes.RecordStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCT_GL_ACCOUNT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GlAccount {
    @Id @Column(name = "GL_ACCOUNT_ID", length = 36) private String id;
    @Column(name = "GL_CODE", length = 40, nullable = false, unique = true) private String glCode;
    @Column(name = "NAME", length = 160, nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "ACCOUNT_TYPE", length = 20, nullable = false) private GlAccountType accountType;
    @Enumerated(EnumType.STRING) @Column(name = "NORMAL_BALANCE", length = 10, nullable = false) private NormalBalance normalBalance;
    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false) private String currencyCode;
    @Column(name = "PARENT_GL_CODE", length = 40) private String parentGlCode;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 20, nullable = false) private RecordStatus status;
    @Version @Column(name = "VERSION_NO", nullable = false) private long version;
    @Column(name = "CREATED_AT", nullable = false, updatable = false) private OffsetDateTime createdAt;

    public GlAccount(String id, String glCode, String name, GlAccountType accountType, NormalBalance normalBalance,
                     String currencyCode, String parentGlCode) {
        this.id = id; this.glCode = glCode; this.name = name; this.accountType = accountType;
        this.normalBalance = normalBalance; this.currencyCode = currencyCode; this.parentGlCode = parentGlCode;
        this.status = RecordStatus.ACTIVE; this.createdAt = OffsetDateTime.now();
    }

    public void changeStatus(RecordStatus status) { this.status = status; }
}
