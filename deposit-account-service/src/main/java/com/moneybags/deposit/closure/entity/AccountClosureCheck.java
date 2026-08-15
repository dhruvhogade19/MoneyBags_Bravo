package com.moneybags.deposit.closure.entity;

import com.moneybags.deposit.domain.DomainTypes.ClosureCheckStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity @Table(name="ACCOUNT_CLOSURE_CHECK",uniqueConstraints=@UniqueConstraint(name="UQ_CLOSURE_CHECK",columnNames={"CLOSURE_REQUEST_ID","CHECK_CODE"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AccountClosureCheck {
    @Id @Column(name="CHECK_ID",length=36) private String id;
    @Column(name="CLOSURE_REQUEST_ID",length=36,nullable=false) private String closureRequestId;
    @Column(name="CHECK_CODE",length=60,nullable=false) private String checkCode;
    @Enumerated(EnumType.STRING) @Column(name="CHECK_STATUS",length=10,nullable=false) private ClosureCheckStatus status;
    @Column(name="DETAILS",length=1000) private String details;
    @Column(name="CHECKED_AT",nullable=false) private OffsetDateTime checkedAt;
    public AccountClosureCheck(String id,String requestId,String code,boolean passed,String details){this.id=id;this.closureRequestId=requestId;this.checkCode=code;this.status=passed?ClosureCheckStatus.PASSED:ClosureCheckStatus.FAILED;this.details=details;this.checkedAt=OffsetDateTime.now();}
}
