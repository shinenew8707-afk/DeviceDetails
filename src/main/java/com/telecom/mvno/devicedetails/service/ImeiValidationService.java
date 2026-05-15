package com.telecom.mvno.devicedetails.service;

import com.telecom.mvno.devicedetails.domain.response.ImeiValidationResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class ImeiValidationService {

    private final Counter successCounter;
    private final Counter failureCounter;

    public ImeiValidationService(MeterRegistry meterRegistry) {
        this.successCounter = meterRegistry.counter("imei.validation.success");
        this.failureCounter = meterRegistry.counter("imei.validation.failure");
    }

    public ImeiValidationResponse validate(String imei) {
        if (!isLuhnValid(imei)) {
            failureCounter.increment();
            return ImeiValidationResponse.invalid(imei, "Failed Luhn check");
        }
        successCounter.increment();
        return ImeiValidationResponse.valid(imei);
    }

    // Luhn algorithm (ISO/IEC 7812)
    private boolean isLuhnValid(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
