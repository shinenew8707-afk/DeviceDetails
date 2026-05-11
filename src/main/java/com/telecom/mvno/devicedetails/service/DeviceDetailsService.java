package com.telecom.mvno.devicedetails.service;

import com.telecom.mvno.devicedetails.client.VendorClient;
import com.telecom.mvno.devicedetails.domain.response.DeviceDetailsResponse;
import com.telecom.mvno.devicedetails.domain.response.VendorDeviceResponse;
import com.telecom.mvno.devicedetails.mapper.ResponseMapper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class DeviceDetailsService {

    private static final String CACHE_NAME = "deviceDetails";

    private final TenantIsolationService tenantIsolationService;
    private final VendorClient vendorClient;
    private final ResponseMapper responseMapper;
    @Nullable
    private final CacheManager cacheManager;

    public DeviceDetailsService(TenantIsolationService tenantIsolationService,
                                VendorClient vendorClient,
                                ResponseMapper responseMapper,
                                @Nullable CacheManager cacheManager) {
        this.tenantIsolationService = tenantIsolationService;
        this.vendorClient = vendorClient;
        this.responseMapper = responseMapper;
        this.cacheManager = cacheManager;
    }

    public DeviceDetailsResponse getDeviceDetails(String msisdn, String mvno) {
        tenantIsolationService.assertIdentityMatch(mvno);

        String cacheKey = mvno + ":" + msisdn;

        if (cacheManager != null) {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                DeviceDetailsResponse cached = cache.get(cacheKey, DeviceDetailsResponse.class);
                if (cached != null) {
                    return cached;
                }
            }
        }

        VendorDeviceResponse vendorResponse = vendorClient.fetchDeviceDetails(msisdn, mvno);
        DeviceDetailsResponse response = responseMapper.map(vendorResponse, msisdn, mvno);

        if (cacheManager != null) {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.put(cacheKey, response);
            }
        }

        return response;
    }
}
