package com.moneybags.identity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.NumericBooleanConverter;

@Entity
@Table(name = "BANK_USER")
public class BankUser {
    @Id
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "USER_ID", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "USERNAME", length = 100, nullable = false, unique = true)
    private String username;

    @Column(name = "PASSWORD_HASH", length = 200, nullable = false)
    private String passwordHash;

    @Column(name = "CUSTOMER_ID", length = 64)
    private String customerId;

    @Column(name = "TENANT_ID", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "ROLES", length = 200, nullable = false)
    private String roles;

    @Column(name = "ENABLED", nullable = false)
    @Convert(converter = NumericBooleanConverter.class)
    private boolean enabled;

    @Column(name = "ACCOUNT_NON_LOCKED", nullable = false)
    @Convert(converter = NumericBooleanConverter.class)
    private boolean accountNonLocked;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private OffsetDateTime updatedAt;

    protected BankUser() {
    }

    public BankUser(String username, String passwordHash, String customerId, String tenantId, String roles) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.passwordHash = passwordHash;
        this.customerId = customerId;
        this.tenantId = tenantId;
        this.roles = roles;
        this.enabled = true;
        this.accountNonLocked = true;
    }

    @PrePersist
    void created() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = OffsetDateTime.now();
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getCustomerId() { return customerId; }
    public String getTenantId() { return tenantId; }
    public String getRoles() { return roles; }
    public boolean isEnabled() { return enabled; }
    public boolean isAccountNonLocked() { return accountNonLocked; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setAccountNonLocked(boolean accountNonLocked) { this.accountNonLocked = accountNonLocked; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
}
