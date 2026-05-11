package com.telecom.mvno.devicedetails.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "device-details.vendor")
public class VendorProperties {

    private String baseUrl;
    private String devicePath = "/devices";
    private String authHeaderName;
    private String authHeaderValue;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getDevicePath() { return devicePath; }
    public void setDevicePath(String devicePath) { this.devicePath = devicePath; }

    public String getAuthHeaderName() { return authHeaderName; }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }

    public String getAuthHeaderValue() { return authHeaderValue; }
    public void setAuthHeaderValue(String authHeaderValue) { this.authHeaderValue = authHeaderValue; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
