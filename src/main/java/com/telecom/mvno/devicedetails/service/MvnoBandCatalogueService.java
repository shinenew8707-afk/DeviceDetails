package com.telecom.mvno.devicedetails.service;

import com.telecom.mvno.devicedetails.config.BandCatalogueProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MvnoBandCatalogueService {

    private final BandCatalogueProperties properties;

    public MvnoBandCatalogueService(BandCatalogueProperties properties) {
        this.properties = properties;
    }

    public List<String> getRequiredBands(String mvnoId) {
        List<String> bands = properties.getBandRequirements().get(mvnoId);
        if (bands == null || bands.isEmpty()) {
            throw new com.telecom.mvno.devicedetails.exception.UnknownMvnoException(
                    "No band configuration found for MVNO: " + mvnoId);
        }
        return bands;
    }
}
