package com.moneybags.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PAYMENT_ATTEMPT")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAttempt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ATTEMPT_ID")
  private Long id;

  @Column(name = "PAYMENT_ID", length = 36, nullable = false)
  private String paymentId;

  @Column(name = "STEP_CODE", length = 60, nullable = false)
  private String stepCode;

  @Column(name = "TARGET_SERVICE", length = 60, nullable = false)
  private String targetService;

  @Column(name = "OUTCOME", length = 30, nullable = false)
  private String outcome;

  @Column(name = "HTTP_STATUS")
  private Integer httpStatus;

  @Column(name = "EXTERNAL_REFERENCE", length = 150)
  private String externalReference;

  @Column(name = "ERROR_CODE", length = 100)
  private String errorCode;

  @Column(name = "STARTED_AT", nullable = false)
  private Instant startedAt;

  @Column(name = "COMPLETED_AT", nullable = false)
  private Instant completedAt;
}
