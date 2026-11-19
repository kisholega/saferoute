package com.saferoute.ai.service.safety;

import java.util.List;

import com.saferoute.ai.geo.GeoPoint;

/**
 * Street-level facts around a route, from OpenStreetMap.
 *
 * @param help      police stations, hospitals, clinics, pharmacies, fuel stations
 * @param lamps     street lamps
 * @param litRoads  centre points of road pieces tagged as lit
 * @param activity  shops, eateries, banks, ATMs and other places where people are around
 * @param transit   bus stops and stations
 * @param live      false when OpenStreetMap could not be reached (features are then estimated)
 */
public record SafetyData(List<Poi> help, List<GeoPoint> lamps, List<GeoPoint> litRoads,
                         List<Poi> activity, List<GeoPoint> transit, boolean live) {

    public static SafetyData unavailable() {
        return new SafetyData(List.of(), List.of(), List.of(), List.of(), List.of(), false);
    }

    /**
     * @param kind    POLICE, HOSPITAL, CLINIC, PHARMACY, FUEL for help points; SHOP, FOOD, BANK, PLACE for activity
     * @param open24h known to be open around the clock
     */
    public record Poi(long osmId, String kind, String name, double lat, double lon, boolean open24h) {
        public GeoPoint point() {
            return new GeoPoint(lat, lon);
        }
    }
}
