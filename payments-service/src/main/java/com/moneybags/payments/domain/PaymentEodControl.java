package com.moneybags.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.type.NumericBooleanConverter;

@Entity
@Table(name = "PAYMENT_EOD_CONTROL")
public class PaymentEodControl {
  @Id
  @Column(name = "CONTROL_ID", length = 40, nullable = false, updatable = false)
  private String controlId;

  @Column(name = "BUSINESS_DATE")
  private LocalDate businessDate;

  @Column(name = "CURRENCY_CODE", length = 3)
  private String currencyCode;

  @Column(name = "COMMAND_REFERENCE", length = 100)
  private String commandReference;

  @Convert(converter = NumericBooleanConverter.class)
  @Column(name = "INTAKE_OPEN", nullable = false, columnDefinition = "NUMBER(1)")
  private boolean intakeOpen;

  @Column(name = "CUTOFF_AT")
  private Instant cutoffAt;

  @Column(name = "REOPENED_AT")
  private Instant reopenedAt;

  @Column(name = "UPDATED_AT", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "VERSION_NO", nullable = false)
  private long version;

  protected PaymentEodControl() { }

  public boolean intakeOpen() {
    return intakeOpen;
  }

  public LocalDate businessDate() {
    return businessDate;
  }

  public String currencyCode() {
    return currencyCode;
  }

  public String commandReference() {
    return commandReference;
  }

  public void cutoff(LocalDate date, String currency, String reference) {
    businessDate = date;
    currencyCode = currency;
    commandReference = reference;
    intakeOpen = false;
    cutoffAt = Instant.now();
    updatedAt = cutoffAt;
  }

  public void reopen() {
    intakeOpen = true;
    reopenedAt = Instant.now();
    updatedAt = reopenedAt;
  }

  public void reopen(LocalDate nextBusinessDate, String currency, String reference) {
    businessDate = nextBusinessDate;
    currencyCode = currency;
    commandReference = reference;
    reopen();
  }

  public void acknowledgeOpen(LocalDate date, String currency, String reference) {
    if (!intakeOpen) throw new IllegalStateException("Cannot acknowledge an open EOD control while intake is closed");
    businessDate = date;
    currencyCode = currency;
    commandReference = reference;
    updatedAt = Instant.now();
  }
}
