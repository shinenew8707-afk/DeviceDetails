package com.telecom.mvno.devicedetails.client;

import com.telecom.mvno.devicedetails.config.VendorProperties;
import com.telecom.mvno.devicedetails.domain.response.VendorDeviceResponse;
import com.telecom.mvno.devicedetails.exception.SubscriberNotFoundException;
import com.telecom.mvno.devicedetails.exception.VendorUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class VendorClient {

    private static final Logger log = LoggerFactory.getLogger(VendorClient.class);

    private final WebClient webClient;
    private final VendorProperties vendorProperties;

    public VendorClient(WebClient vendorWebClient, VendorProperties vendorProperties) {
        this.webClient = vendorWebClient;
        this.vendorProperties = vendorProperties;
    }

    public VendorDeviceResponse fetchDeviceDetails(String msisdn, String mvno) {
        try {
            return fetchDeviceDetailsAsync(msisdn, mvno).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VendorUnavailableException("Vendor call interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SubscriberNotFoundException snfe) {
                throw snfe;
            }
            if (cause instanceof VendorUnavailableException vue) {
                throw vue;
            }
            throw new VendorUnavailableException("Vendor call failed", cause);
        }
    }

    @CircuitBreaker(name = "vendorBackend", fallbackMethod = "circuitBreakerFallback")
    @Retry(name = "vendorBackend", fallbackMethod = "retryFallback")
    @TimeLimiter(name = "vendorBackend")
    public CompletableFuture<VendorDeviceResponse> fetchDeviceDetailsAsync(String msisdn, String mvno) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(vendorProperties.getDevicePath())
                        .queryParam("msisdn", msisdn)
                        .build())
                .retrieve()
                .onStatus(
                        status -> status == HttpStatus.NOT_FOUND,
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> (Throwable) new SubscriberNotFoundException(
                                        "Subscriber not found for msisdn: " + msisdn))
                                .defaultIfEmpty(new SubscriberNotFoundException(
                                        "Subscriber not found for msisdn: " + msisdn))
                                .flatMap(Mono::error))
                .onStatus(
                        status -> status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .map(body -> (Throwable) new VendorUnavailableException(
                                        "Vendor backend returned server error: " + body))
                                .defaultIfEmpty(new VendorUnavailableException("Vendor backend returned server error"))
                                .flatMap(Mono::error))
                .bodyToMono(VendorDeviceResponse.class)
                .toFuture();
    }

    public CompletableFuture<VendorDeviceResponse> circuitBreakerFallback(String msisdn, String mvno, Throwable t) {
        log.warn("Circuit breaker open for vendorBackend, msisdn={}, cause={}", msisdn, t.getMessage());
        CompletableFuture<VendorDeviceResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new VendorUnavailableException(
                "Vendor backend is currently unavailable (circuit open)", t));
        return future;
    }

    public CompletableFuture<VendorDeviceResponse> retryFallback(String msisdn, String mvno, Throwable t) {
        log.warn("All retry attempts exhausted for vendorBackend, msisdn={}, cause={}", msisdn, t.getMessage());
        CompletableFuture<VendorDeviceResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new VendorUnavailableException(
                "Vendor backend is currently unavailable after retries", t));
        return future;
    }
}
