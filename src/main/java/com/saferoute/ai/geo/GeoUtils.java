package com.saferoute.ai.geo;

import java.util.ArrayList;
import java.util.List;

/** Small geometry helpers. Accurate to a few metres at city scale, which is all route scoring needs. */
public final class GeoUtils {

    public static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtils() {
    }

    /** Great-circle (haversine) distance in metres. */
    public static double distanceMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /**
     * Shortest distance in metres from point p to the line segment a-b. Uses a local flat projection
     * centred on p, which is accurate for the short segments of a route.
     */
    public static double distanceToSegmentMeters(GeoPoint p, GeoPoint a, GeoPoint b) {
        double metersPerDegLat = Math.toRadians(1) * EARTH_RADIUS_M;
        double metersPerDegLon = metersPerDegLat * Math.cos(Math.toRadians(p.lat()));
        double ax = (a.lon() - p.lon()) * metersPerDegLon;
        double ay = (a.lat() - p.lat()) * metersPerDegLat;
        double bx = (b.lon() - p.lon()) * metersPerDegLon;
        double by = (b.lat() - p.lat()) * metersPerDegLat;
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSquared));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }

    /** Shortest distance in metres from point p to any part of the polyline. */
    public static double distanceToPolylineMeters(GeoPoint p, List<GeoPoint> line) {
        if (line == null || line.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        if (line.size() == 1) {
            return distanceMeters(p, line.get(0));
        }
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < line.size(); i++) {
            best = Math.min(best, distanceToSegmentMeters(p, line.get(i - 1), line.get(i)));
        }
        return best;
    }

    /** Evenly spaced points on the straight line from a to b, including both ends. */
    public static List<GeoPoint> interpolate(GeoPoint a, GeoPoint b, int segments) {
        List<GeoPoint> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            points.add(new GeoPoint(a.lat() + (b.lat() - a.lat()) * t, a.lon() + (b.lon() - a.lon()) * t));
        }
        return points;
    }
}
