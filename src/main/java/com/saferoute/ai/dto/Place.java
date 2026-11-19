package com.saferoute.ai.dto;

import com.saferoute.ai.geo.GeoPoint;

/**
 * A named location.
 *
 * @param source where it came from: "index" (built-in OpenStreetMap place list), "photon", "nominatim",
 *               or "user" (coordinates picked on the map, from GPS, or from a suggestion)
 * @param type   what kind of place, e.g. "Suburb", "Metro station", "Mall" (may be null)
 */
public record Place(String name, String displayName, double latitude, double longitude, String source, String type) {

    public Place(String name, String displayName, double latitude, double longitude, String source) {
        this(name, displayName, latitude, longitude, source, null);
    }

    public GeoPoint point() {
        return new GeoPoint(latitude, longitude);
    }
}
