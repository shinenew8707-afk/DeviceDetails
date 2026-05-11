package com.telecom.mvno.devicedetails.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public TimeLimiterConfig vendorBackendTimeLimiterConfig() {
        return TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(5))
                .build();
    }

    @Bean
    public RetryConfig vendorBackendRetryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .enableExponentialBackoff()
                .exponentialBackoffMultiplier(2.0)
                .maxWaitDuration(Duration.ofSeconds(4))
                .retryExceptions(java.io.IOException.class, java.util.concurrent.TimeoutException.class)
                .build();
    }

    @Bean
    public CircuitBreakerConfig vendorBackendCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .slowCallRateThreshold(80)
                .build();
    }
}
