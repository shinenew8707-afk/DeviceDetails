package com.telecom.mvno.devicedetails.domain.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VendorDeviceResponse {

    private String msisdn;
    private String deviceName;
    private String make;
    private String model;
    private Boolean hasVolte;
    private String imei;
    private String imsi;
    private List<String> supportedBands;
    private Map<String, Object> additionalAttributes;

    public VendorDeviceResponse() {
    }

    public VendorDeviceResponse(String msisdn, String deviceName, String make, String model,
                                Boolean hasVolte, String imei, String imsi,
                                Map<String, Object> additionalAttributes) {
        this.msisdn = msisdn;
        this.deviceName = deviceName;
        this.make = make;
        this.model = model;
        this.hasVolte = hasVolte;
        this.imei = imei;
        this.imsi = imsi;
        this.additionalAttributes = additionalAttributes;
    }

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Boolean getHasVolte() { return hasVolte; }
    public void setHasVolte(Boolean hasVolte) { this.hasVolte = hasVolte; }

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public String getImsi() { return imsi; }
    public void setImsi(String imsi) { this.imsi = imsi; }

    public List<String> getSupportedBands() { return supportedBands; }
    public void setSupportedBands(List<String> supportedBands) { this.supportedBands = supportedBands; }

    public Map<String, Object> getAdditionalAttributes() { return additionalAttributes; }
    public void setAdditionalAttributes(Map<String, Object> additionalAttributes) {
        this.additionalAttributes = additionalAttributes;
    }
}
