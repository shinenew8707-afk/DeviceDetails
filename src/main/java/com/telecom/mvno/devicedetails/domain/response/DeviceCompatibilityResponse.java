package com.telecom.mvno.devicedetails.domain.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceCompatibilityResponse {

    private final String imei;
    private final String mvno;
    private final boolean compatible;
    private final List<String> supportedBands;
    private final List<String> requiredBands;
    private final List<String> matchedBands;
    private final String reason;

    private DeviceCompatibilityResponse(Builder builder) {
        this.imei = builder.imei;
        this.mvno = builder.mvno;
        this.compatible = builder.compatible;
        this.supportedBands = builder.supportedBands;
        this.requiredBands = builder.requiredBands;
        this.matchedBands = builder.matchedBands;
        this.reason = builder.reason;
    }

    public String getImei() { return imei; }
    public String getMvno() { return mvno; }
    public boolean isCompatible() { return compatible; }
    public List<String> getSupportedBands() { return supportedBands; }
    public List<String> getRequiredBands() { return requiredBands; }
    public List<String> getMatchedBands() { return matchedBands; }
    public String getReason() { return reason; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String imei;
        private String mvno;
        private boolean compatible;
        private List<String> supportedBands;
        private List<String> requiredBands;
        private List<String> matchedBands;
        private String reason;

        public Builder imei(String imei) { this.imei = imei; return this; }
        public Builder mvno(String mvno) { this.mvno = mvno; return this; }
        public Builder compatible(boolean compatible) { this.compatible = compatible; return this; }
        public Builder supportedBands(List<String> bands) { this.supportedBands = bands; return this; }
        public Builder requiredBands(List<String> bands) { this.requiredBands = bands; return this; }
        public Builder matchedBands(List<String> bands) { this.matchedBands = bands; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public DeviceCompatibilityResponse build() { return new DeviceCompatibilityResponse(this); }
    }
}
