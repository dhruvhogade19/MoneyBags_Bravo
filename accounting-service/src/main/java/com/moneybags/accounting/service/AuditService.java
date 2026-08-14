package com.moneybags.accounting.service;

import com.moneybags.accounting.entity.AuditLog;
import com.moneybags.accounting.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {
    private final AuditLogRepository audits;
    public AuditService(AuditLogRepository audits) { this.audits = audits; }
    public void record(String aggregate, String action, String outcome, String actor, String actorType,
                       String correlationId) {
        audits.save(new AuditLog(UUID.randomUUID().toString(), aggregate, action, outcome, actor, actorType,
                correlationId));
    }
}
