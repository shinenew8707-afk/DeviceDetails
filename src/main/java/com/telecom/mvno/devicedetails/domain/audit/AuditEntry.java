package com.telecom.mvno.devicedetails.domain.audit;

import java.time.Instant;

public class AuditEntry {

    public enum Outcome {
        SUCCESS, NOT_FOUND, VENDOR_ERROR, AUTH_FAILURE, VALIDATION_ERROR, CACHE_HIT, INTERNAL_ERROR
    }

    private String correlationId;
    private Instant timestamp;
    private String mvno;
    private String msisdn;
    private String callerIp;
    private String credentialId;
    private String httpMethod;
    private String endpoint;
    private int responseStatus;
    private long durationMs;
    private Outcome outcome;

    public AuditEntry() {
    }

    public AuditEntry(String correlationId, Instant timestamp, String mvno, String msisdn,
                      String callerIp, String credentialId, String httpMethod, String endpoint,
                      int responseStatus, long durationMs, Outcome outcome) {
        this.correlationId = correlationId;
        this.timestamp = timestamp;
        this.mvno = mvno;
        this.msisdn = msisdn;
        this.callerIp = callerIp;
        this.credentialId = credentialId;
        this.httpMethod = httpMethod;
        this.endpoint = endpoint;
        this.responseStatus = responseStatus;
        this.durationMs = durationMs;
        this.outcome = outcome;
    }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getMvno() { return mvno; }
    public void setMvno(String mvno) { this.mvno = mvno; }

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }

    public String getCallerIp() { return callerIp; }
    public void setCallerIp(String callerIp) { this.callerIp = callerIp; }

    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public int getResponseStatus() { return responseStatus; }
    public void setResponseStatus(int responseStatus) { this.responseStatus = responseStatus; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
}
