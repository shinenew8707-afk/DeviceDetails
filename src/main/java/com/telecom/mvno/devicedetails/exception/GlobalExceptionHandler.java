package com.telecom.mvno.devicedetails.exception;

import com.telecom.mvno.devicedetails.domain.response.ApiErrorResponse;
import com.telecom.mvno.devicedetails.domain.response.Violation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String MDC_CORRELATION_ID = "correlationId";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new Violation(
                        fe.getField(),
                        fe.getRejectedValue() != null ? fe.getRejectedValue().toString() : null,
                        fe.getDefaultMessage()))
                .toList();
        String errorCode = violations.stream().anyMatch(v -> "msisdn".equals(v.field()))
                ? "INVALID_MSISDN" : "INVALID_MVNO_NAME";
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(errorCode, "Validation failed", correlationId(), violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<Violation> violations = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String field = cv.getPropertyPath().toString();
                    String rejected = cv.getInvalidValue() != null ? cv.getInvalidValue().toString() : null;
                    return new Violation(field, rejected, cv.getMessage());
                })
                .toList();

        String errorCode = violations.stream()
                .anyMatch(v -> v.field().contains("msisdn")) ? "INVALID_MSISDN" : "INVALID_MVNO_NAME";

        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(errorCode, "Validation failed", correlationId(), violations));
    }

    @ExceptionHandler(com.telecom.mvno.devicedetails.exception.AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            com.telecom.mvno.devicedetails.exception.AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("AUTHENTICATION_FAILED", ex.getMessage(), correlationId(), List.of()));
    }

    @ExceptionHandler({com.telecom.mvno.devicedetails.exception.AccessDeniedException.class,
            AccessDeniedException.class})
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse("ACCESS_DENIED", ex.getMessage(), correlationId(), List.of()));
    }

    @ExceptionHandler(SubscriberNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSubscriberNotFound(SubscriberNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("SUBSCRIBER_NOT_FOUND", ex.getMessage(), correlationId(), List.of()));
    }

    @ExceptionHandler(VendorUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleVendorUnavailable(VendorUnavailableException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "30");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .headers(headers)
                .body(new ApiErrorResponse("VENDOR_UNAVAILABLE", ex.getMessage(), correlationId(), List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", "An unexpected error occurred",
                        correlationId(), List.of()));
    }

    private String correlationId() {
        return MDC.get(MDC_CORRELATION_ID);
    }
}
