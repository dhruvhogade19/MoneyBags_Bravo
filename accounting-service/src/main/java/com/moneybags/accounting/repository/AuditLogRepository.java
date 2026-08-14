package com.moneybags.accounting.repository;

import com.moneybags.accounting.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {}
