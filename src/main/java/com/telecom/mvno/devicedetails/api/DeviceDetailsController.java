package com.telecom.mvno.devicedetails.api;

import com.telecom.mvno.devicedetails.domain.response.DeviceDetailsResponse;
import com.telecom.mvno.devicedetails.service.DeviceDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@Validated
public class DeviceDetailsController {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final DeviceDetailsService deviceDetailsService;

    public DeviceDetailsController(DeviceDetailsService deviceDetailsService) {
        this.deviceDetailsService = deviceDetailsService;
    }

    @GetMapping
    public ResponseEntity<DeviceDetailsResponse> getDeviceDetails(
            @RequestParam
            @NotBlank(message = "msisdn must not be blank")
            @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "msisdn must be in E.164 format")
            String msisdn,
            @RequestParam
            @NotBlank(message = "mvno must not be blank")
            String mvno,
            HttpServletResponse response) {

        String correlationId = MDC.get(MDC_CORRELATION_ID);
        if (correlationId != null) {
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
        }

        DeviceDetailsResponse deviceDetails = deviceDetailsService.getDeviceDetails(msisdn, mvno);
        return ResponseEntity.ok(deviceDetails);
    }
}
