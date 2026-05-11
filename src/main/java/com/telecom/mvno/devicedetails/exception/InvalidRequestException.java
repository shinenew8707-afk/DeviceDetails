package com.telecom.mvno.devicedetails.exception;

import com.telecom.mvno.devicedetails.domain.response.Violation;

import java.util.List;

public class InvalidRequestException extends RuntimeException {

    private final List<Violation> violations;

    public InvalidRequestException(String message, List<Violation> violations) {
        super(message);
        this.violations = violations;
    }

    public List<Violation> getViolations() {
        return violations;
    }
}
