package com.telecom.mvno.devicedetails.mapper;

import com.telecom.mvno.devicedetails.domain.response.DeviceDetailsResponse;
import com.telecom.mvno.devicedetails.domain.response.VendorDeviceResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class ResponseMapper {

    private static final String MDC_CORRELATION_ID = "correlationId";

    public DeviceDetailsResponse map(VendorDeviceResponse vendor, String msisdn, String mvno) {
        return new DeviceDetailsResponse(
                msisdn,
                mvno,
                vendor != null ? vendor.getDeviceName() : null,
                vendor != null ? vendor.getMake() : null,
                vendor != null ? vendor.getModel() : null,
                vendor != null ? vendor.getHasVolte() : null,
                vendor != null ? vendor.getImei() : null,
                vendor != null ? vendor.getImsi() : null,
                vendor != null ? vendor.getAdditionalAttributes() : null,
                MDC.get(MDC_CORRELATION_ID)
        );
    }
}
