package com.saferoute.ai.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * A route line with precomputed lengths. Supports resampling at even spacing, projecting a position
 * onto the route (how far along, how far off), and measuring how much two routes overlap.
 */
public final class Polyline {

    private final List<GeoPoint> points;
    private final double[] cumulative; // metres from the start to each vertex

    public Polyline(List<GeoPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("A route needs at least one point.");
        }
        this.points = List.copyOf(points);
        this.cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + GeoUtils.distanceMeters(points.get(i - 1), points.get(i));
        }
    }

    public List<GeoPoint> points() {
        return points;
    }

    public double lengthMeters() {
        return cumulative[cumulative.length - 1];
    }

    /** Metres from the start to vertex i. */
    public double distanceAt(int index) {
        return cumulative[index];
    }

    /** The point that lies the given distance along the route. */
    public GeoPoint pointAt(double meters) {
        if (meters <= 0) {
            return points.get(0);
        }
        if (meters >= lengthMeters()) {
            return points.get(points.size() - 1);
        }
        int i = 1;
        while (cumulative[i] < meters) {
            i++;
        }
        double segment = cumulative[i] - cumulative[i - 1];
        double t = segment == 0 ? 0 : (meters - cumulative[i - 1]) / segment;
        GeoPoint a = points.get(i - 1);
        GeoPoint b = points.get(i);
        return new GeoPoint(a.lat() + (b.lat() - a.lat()) * t, a.lon() + (b.lon() - a.lon()) * t);
    }

    /** Points every {@code stepMeters} along the route, always including the start and the end. */
    public List<Sample> resample(double stepMeters) {
        List<Sample> samples = new ArrayList<>();
        double length = lengthMeters();
        int count = Math.max(1, (int) Math.ceil(length / stepMeters));
        for (int i = 0; i <= count; i++) {
            double along = Math.min(length, i * stepMeters);
            samples.add(new Sample(pointAt(along), along, vertexIndexAt(along)));
        }
        return samples;
    }

    /** Index of the vertex at or just before the given distance (for colouring the original geometry). */
    public int vertexIndexAt(double meters) {
        int lo = 0;
        int hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (cumulative[mid] <= meters) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** Where a position is relative to the route: metres off the route and metres along it. */
    public Projection project(GeoPoint p) {
        if (points.size() == 1) {
            return new Projection(GeoUtils.distanceMeters(p, points.get(0)), 0);
        }
        double metersPerDegLat = Math.toRadians(1) * GeoUtils.EARTH_RADIUS_M;
        double metersPerDegLon = metersPerDegLat * Math.cos(Math.toRadians(p.lat()));
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestAlong = 0;
        for (int i = 1; i < points.size(); i++) {
            GeoPoint a = points.get(i - 1);
            GeoPoint b = points.get(i);
            double ax = (a.lon() - p.lon()) * metersPerDegLon;
            double ay = (a.lat() - p.lat()) * metersPerDegLat;
            double dx = (b.lon() - a.lon()) * metersPerDegLon;
            double dy = (b.lat() - a.lat()) * metersPerDegLat;
            double lengthSquared = dx * dx + dy * dy;
            double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / lengthSquared));
            double distance = Math.hypot(ax + t * dx, ay + t * dy);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestAlong = cumulative[i - 1] + t * (cumulative[i] - cumulative[i - 1]);
            }
        }
        return new Projection(bestDistance, bestAlong);
    }

    /** Share (0..1) of this route that runs within {@code toleranceMeters} of the other route. */
    public double overlapWith(Polyline other, double toleranceMeters) {
        List<Sample> samples = resample(50);
        long close = samples.stream()
                .filter(s -> GeoUtils.distanceToPolylineMeters(s.point(), other.points()) <= toleranceMeters)
                .count();
        return (double) close / samples.size();
    }

    public record Sample(GeoPoint point, double alongMeters, int vertexIndex) {
    }

    public record Projection(double offRouteMeters, double alongMeters) {
    }
}
