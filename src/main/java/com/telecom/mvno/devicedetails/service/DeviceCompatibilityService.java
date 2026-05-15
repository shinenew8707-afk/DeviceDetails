package com.telecom.mvno.devicedetails.service;

import com.telecom.mvno.devicedetails.client.VendorClient;
import com.telecom.mvno.devicedetails.domain.response.DeviceCompatibilityResponse;
import com.telecom.mvno.devicedetails.domain.response.VendorDeviceResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeviceCompatibilityService {

    private final TenantIsolationService tenantIsolationService;
    private final VendorClient vendorClient;
    private final MvnoBandCatalogueService bandCatalogueService;
    private final Counter compatibleCounter;
    private final Counter incompatibleCounter;
    private final Counter vendorErrorCounter;

    public DeviceCompatibilityService(TenantIsolationService tenantIsolationService,
                                      VendorClient vendorClient,
                                      MvnoBandCatalogueService bandCatalogueService,
                                      MeterRegistry meterRegistry) {
        this.tenantIsolationService = tenantIsolationService;
        this.vendorClient = vendorClient;
        this.bandCatalogueService = bandCatalogueService;
        this.compatibleCounter = meterRegistry.counter("device.compatibility.check.compatible");
        this.incompatibleCounter = meterRegistry.counter("device.compatibility.check.incompatible");
        this.vendorErrorCounter = meterRegistry.counter("device.compatibility.check.vendor_error");
    }

    public DeviceCompatibilityResponse checkCompatibility(String imei, String mvno) {
        tenantIsolationService.assertIdentityMatch(mvno);

        VendorDeviceResponse vendorResponse;
        try {
            vendorResponse = vendorClient.fetchDeviceDetails(imei, mvno);
        } catch (Exception e) {
            vendorErrorCounter.increment();
            throw e;
        }

        List<String> supportedBands = vendorResponse.getSupportedBands() != null
                ? vendorResponse.getSupportedBands()
                : List.of();

        List<String> requiredBands = bandCatalogueService.getRequiredBands(mvno);

        List<String> matchedBands = supportedBands.stream()
                .filter(requiredBands::contains)
                .collect(Collectors.toList());

        boolean compatible = matchedBands.containsAll(requiredBands);

        if (compatible) {
            compatibleCounter.increment();
            return DeviceCompatibilityResponse.builder()
                    .imei(imei).mvno(mvno).compatible(true)
                    .supportedBands(new ArrayList<>(supportedBands))
                    .requiredBands(new ArrayList<>(requiredBands))
                    .matchedBands(matchedBands)
                    .build();
        } else {
            incompatibleCounter.increment();
            List<String> missingBands = requiredBands.stream()
                    .filter(b -> !matchedBands.contains(b))
                    .collect(Collectors.toList());
            String reason = missingBands.isEmpty()
                    ? "No matching bands"
                    : "Missing bands: " + String.join(", ", missingBands);
            return DeviceCompatibilityResponse.builder()
                    .imei(imei).mvno(mvno).compatible(false)
                    .supportedBands(new ArrayList<>(supportedBands))
                    .requiredBands(new ArrayList<>(requiredBands))
                    .matchedBands(matchedBands)
                    .reason(reason)
                    .build();
        }
    }
}
