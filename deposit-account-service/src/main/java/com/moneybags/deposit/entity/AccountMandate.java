package com.moneybags.deposit.entity;

import com.moneybags.deposit.domain.DomainTypes.RecordStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCOUNT_MANDATE")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountMandate {
    @Id @Column(name = "MANDATE_ID", length = 36)
    private String id;
    @Column(name = "ACCOUNT_ID", length = 36, nullable = false)
    private String accountId;
    @Column(name = "AUTHORIZED_CUSTOMER_ID", length = 36, nullable = false)
    private String authorizedCustomerId;
    @Column(name = "MANDATE_TYPE", length = 30, nullable = false)
    private String mandateType;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 16, nullable = false)
    private RecordStatus status;
    @Column(name = "VALID_FROM", nullable = false)
    private OffsetDateTime validFrom;
    @Column(name = "VALID_TO")
    private OffsetDateTime validTo;

    public AccountMandate(String id, String accountId, String authorizedCustomerId, String mandateType,
                          OffsetDateTime validFrom, OffsetDateTime validTo) {
        this.id = id;
        this.accountId = accountId;
        this.authorizedCustomerId = authorizedCustomerId;
        this.mandateType = mandateType;
        this.status = RecordStatus.ACTIVE;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }
}
