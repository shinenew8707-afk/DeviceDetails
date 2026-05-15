package com.telecom.mvno.devicedetails.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ImeiValidationRequest {

    @NotBlank(message = "imei must not be blank")
    @Size(min = 15, max = 15, message = "IMEI must be exactly 15 digits")
    @Pattern(regexp = "\\d+", message = "IMEI must contain digits only")
    private String imei;

    public ImeiValidationRequest() {}

    public ImeiValidationRequest(String imei) {
        this.imei = imei;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }
}
