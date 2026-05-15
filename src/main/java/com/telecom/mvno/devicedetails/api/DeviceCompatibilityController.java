package com.telecom.mvno.devicedetails.api;

import com.telecom.mvno.devicedetails.domain.response.DeviceCompatibilityResponse;
import com.telecom.mvno.devicedetails.service.DeviceCompatibilityService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@Validated
public class DeviceCompatibilityController {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final DeviceCompatibilityService compatibilityService;

    public DeviceCompatibilityController(DeviceCompatibilityService compatibilityService) {
        this.compatibilityService = compatibilityService;
    }

    @GetMapping("/{imei}/compatibility")
    public ResponseEntity<DeviceCompatibilityResponse> checkCompatibility(
            @PathVariable
            @Pattern(regexp = "\\d{15}", message = "IMEI must be exactly 15 digits")
            String imei,
            @RequestParam
            @NotBlank(message = "mvno must not be blank")
            String mvno,
            HttpServletResponse response) {

        String correlationId = MDC.get(MDC_CORRELATION_ID);
        if (correlationId != null) {
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
        }

        return ResponseEntity.ok(compatibilityService.checkCompatibility(imei, mvno));
    }
}
