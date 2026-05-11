package com.telecom.mvno.devicedetails.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(name = "device-details.cache.enabled", havingValue = "true")
@EnableCaching
public class CacheConfig {

    private final long ttlSeconds;
    private final long maxSize;

    public CacheConfig(
            @Value("${device-details.cache.ttl-seconds:300}") long ttlSeconds,
            @Value("${device-details.cache.max-size:10000}") long maxSize) {
        this.ttlSeconds = ttlSeconds;
        this.maxSize = maxSize;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("deviceDetails");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize));
        return cacheManager;
    }
}
