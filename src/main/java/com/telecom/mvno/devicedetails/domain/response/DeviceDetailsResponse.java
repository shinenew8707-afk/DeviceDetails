package com.telecom.mvno.devicedetails.domain.response;

import java.util.Map;

public record DeviceDetailsResponse(
        String msisdn,
        String mvno,
        String deviceName,
        String make,
        String model,
        Boolean hasVolte,
        String imei,
        String imsi,
        Map<String, Object> additionalAttributes,
        String correlationId
) {
}
