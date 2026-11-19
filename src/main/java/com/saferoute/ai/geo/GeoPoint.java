package com.saferoute.ai.geo;

import java.util.List;

/** A latitude/longitude pair in decimal degrees (WGS84). */
public record GeoPoint(double lat, double lon) {

    public GeoPoint {
        if (Double.isNaN(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90.");
        }
        if (Double.isNaN(lon) || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180.");
        }
    }

    /** The [lat, lon] form the frontend (Leaflet) uses. */
    public List<Double> toLatLon() {
        return List.of(lat, lon);
    }
}
