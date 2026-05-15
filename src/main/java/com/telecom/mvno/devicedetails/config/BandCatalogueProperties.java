package com.telecom.mvno.devicedetails.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "mvno")
public class BandCatalogueProperties {

    private Map<String, List<String>> bandRequirements = Collections.emptyMap();

    public Map<String, List<String>> getBandRequirements() {
        return bandRequirements;
    }

    public void setBandRequirements(Map<String, List<String>> bandRequirements) {
        this.bandRequirements = bandRequirements;
    }
}
