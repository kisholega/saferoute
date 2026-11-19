package com.saferoute.ai.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.saferoute.ai.dto.SafeLocation;
import com.saferoute.ai.exception.ExternalServiceException;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.geo.GeoUtils;
import com.saferoute.ai.service.safety.OsmSafetyDataService;
import com.saferoute.ai.service.safety.SafetyData;

/** Real places to get help near a point, live from OpenStreetMap: police, hospitals, clinics, pharmacies, fuel. */
@Service
public class SafeLocationService {

    static final int MAX_RADIUS_METERS = 5000;

    private final OsmSafetyDataService safetyDataService;

    public SafeLocationService(OsmSafetyDataService safetyDataService) {
        this.safetyDataService = safetyDataService;
    }

    public List<SafeLocation> findNearby(double latitude, double longitude, int radiusMeters) {
        GeoPoint origin = new GeoPoint(latitude, longitude);
        int radius = Math.max(200, Math.min(MAX_RADIUS_METERS, radiusMeters));
        SafetyData data = safetyDataService.helpAround(origin, radius);
        if (!data.live()) {
            throw new ExternalServiceException("Live places from OpenStreetMap are unavailable right now. Try again shortly.", null);
        }
        return data.help().stream()
                .map(poi -> new SafeLocation(
                        poi.name().isBlank() ? defaultName(poi.kind()) : poi.name(),
                        poi.kind(), poi.lat(), poi.lon(),
                        Math.round(GeoUtils.distanceMeters(origin, poi.point()) / 10.0) / 100.0,
                        poi.open24h()))
                .sorted(Comparator.comparingDouble(SafeLocation::distanceKm))
                .limit(60)
                .toList();
    }

    private static String defaultName(String kind) {
        return switch (kind) {
            case "POLICE" -> "Police station";
            case "HOSPITAL" -> "Hospital";
            case "CLINIC" -> "Clinic";
            case "PHARMACY" -> "Pharmacy";
            case "FUEL" -> "Fuel station";
            default -> "Help point";
        };
    }
}
