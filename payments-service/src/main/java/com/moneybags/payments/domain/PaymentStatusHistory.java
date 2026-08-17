package com.moneybags.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PAYMENT_STATUS_HISTORY")
@Getter
@Setter
@NoArgsConstructor
public class PaymentStatusHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "HISTORY_ID")
  private Long id;

  @Column(name = "PAYMENT_ID", length = 36, nullable = false)
  private String paymentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "FROM_STATUS", length = 30)
  private PaymentStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "TO_STATUS", length = 30, nullable = false)
  private PaymentStatus toStatus;

  @Column(name = "REASON_CODE", length = 80)
  private String reasonCode;

  @Column(name = "REASON_MESSAGE", length = 500)
  private String reasonMessage;

  @Column(name = "CORRELATION_ID", length = 100, nullable = false)
  private String correlationId;

  @Column(name = "CHANGED_AT", nullable = false)
  private Instant changedAt;
}
