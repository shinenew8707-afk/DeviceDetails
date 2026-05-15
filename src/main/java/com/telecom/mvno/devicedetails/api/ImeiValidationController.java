package com.telecom.mvno.devicedetails.api;

import com.telecom.mvno.devicedetails.domain.request.ImeiValidationRequest;
import com.telecom.mvno.devicedetails.domain.response.ImeiValidationResponse;
import com.telecom.mvno.devicedetails.service.ImeiValidationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class ImeiValidationController {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final ImeiValidationService imeiValidationService;

    public ImeiValidationController(ImeiValidationService imeiValidationService) {
        this.imeiValidationService = imeiValidationService;
    }

    @PostMapping("/validate-imei")
    public ResponseEntity<ImeiValidationResponse> validateImei(
            @RequestBody @Valid ImeiValidationRequest request,
            HttpServletResponse response) {

        String correlationId = MDC.get(MDC_CORRELATION_ID);
        if (correlationId != null) {
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
        }

        ImeiValidationResponse result = imeiValidationService.validate(request.getImei());
        return ResponseEntity.ok(result);
    }
}
