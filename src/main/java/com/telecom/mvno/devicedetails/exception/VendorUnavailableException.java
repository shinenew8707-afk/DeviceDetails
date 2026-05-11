package com.telecom.mvno.devicedetails.exception;

public class VendorUnavailableException extends RuntimeException {

    public VendorUnavailableException(String message) {
        super(message);
    }

    public VendorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
