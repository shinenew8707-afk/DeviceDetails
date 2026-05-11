package com.telecom.mvno.devicedetails.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telecom.mvno.devicedetails.domain.audit.AuditEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");
    private static final int MASKED_SUFFIX_LENGTH = 4;

    private final ObjectMapper objectMapper;

    public AuditLogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Async
    public void log(AuditEntry entry) {
        AuditEntry maskedEntry = new AuditEntry(
                entry.getCorrelationId(),
                entry.getTimestamp(),
                entry.getMvno(),
                maskMsisdn(entry.getMsisdn()),
                entry.getCallerIp(),
                entry.getCredentialId(),
                entry.getHttpMethod(),
                entry.getEndpoint(),
                entry.getResponseStatus(),
                entry.getDurationMs(),
                entry.getOutcome()
        );

        try {
            String json = objectMapper.writeValueAsString(maskedEntry);
            AUDIT_LOG.info(json);
        } catch (JsonProcessingException e) {
            AUDIT_LOG.error("Failed to serialize audit entry: {}", e.getMessage());
        }
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() <= MASKED_SUFFIX_LENGTH) {
            return msisdn;
        }
        String suffix = msisdn.substring(msisdn.length() - MASKED_SUFFIX_LENGTH);
        return "+****" + suffix;
    }
}
