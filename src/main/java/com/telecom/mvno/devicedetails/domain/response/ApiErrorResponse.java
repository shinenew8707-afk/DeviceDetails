package com.telecom.mvno.devicedetails.domain.response;

import java.util.List;

public record ApiErrorResponse(
        String errorCode,
        String message,
        String correlationId,
        List<Violation> violations
) {
}
