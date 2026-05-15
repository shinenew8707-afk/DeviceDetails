package com.telecom.mvno.devicedetails.domain.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImeiValidationResponse {

    private final String imei;
    private final boolean valid;
    private final String reason;

    public ImeiValidationResponse(String imei, boolean valid, String reason) {
        this.imei = imei;
        this.valid = valid;
        this.reason = reason;
    }

    public static ImeiValidationResponse valid(String imei) {
        return new ImeiValidationResponse(imei, true, null);
    }

    public static ImeiValidationResponse invalid(String imei, String reason) {
        return new ImeiValidationResponse(imei, false, reason);
    }

    public String getImei() { return imei; }
    public boolean isValid() { return valid; }
    public String getReason() { return reason; }
}
