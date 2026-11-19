package com.saferoute.ai.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.saferoute.ai.dto.ZoneHit;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.geo.GeoUtils;
import com.saferoute.ai.model.SafetyZone;
import com.saferoute.ai.repository.SafetyZoneRepository;

@Service
public class SafetyZoneService {

    private final SafetyZoneRepository safetyZoneRepository;
    private final boolean demoZonesEnabled;

    public SafetyZoneService(SafetyZoneRepository safetyZoneRepository,
                             @org.springframework.beans.factory.annotation.Value("${saferoute.demo-zones:false}") boolean demoZonesEnabled) {
        this.safetyZoneRepository = safetyZoneRepository;
        this.demoZonesEnabled = demoZonesEnabled;
    }

    /**
     * Flagged zones. The seeded zones are synthetic examples, so they only take part (on the map and in
     * scoring) when demo zones are switched on with saferoute.demo-zones=true / DEMO_ZONES=true.
     */
    public List<SafetyZone> getActiveZones() {
        if (!demoZonesEnabled) {
            return List.of();
        }
        return safetyZoneRepository.findByActiveTrue();
    }

    /**
     * Zones that the route line actually passes through: the shortest distance from the zone centre to
     * any segment of the route is within the zone radius. Most severe zones come first.
     */
    public List<ZoneHit> zonesAlongRoute(List<GeoPoint> route) {
        List<ZoneHit> hits = new ArrayList<>();
        for (SafetyZone zone : getActiveZones()) {
            double distance = GeoUtils.distanceToPolylineMeters(zone.centre(), route);
            if (distance <= zone.radiusMeters()) {
                hits.add(new ZoneHit(zone.getId(), zone.getName(), zone.getRiskLevel(), zone.getDescription()));
            }
        }
        hits.sort(Comparator.comparing((ZoneHit hit) -> hit.riskLevel().getRiskWeight()).reversed());
        return hits;
    }
}
