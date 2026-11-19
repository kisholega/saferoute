package com.saferoute.ai.geo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A simple grid index for "what is near this point?" questions. Points are bucketed into cells of
 * about 250 m, so a search only looks at the few cells around the query point.
 */
public final class PointIndex<T> {

    private static final double CELL_DEGREES = 0.00225; // ~250 m

    private final Map<Long, List<T>> cells = new HashMap<>();
    private final Function<T, GeoPoint> locate;
    private final int size;

    public PointIndex(List<T> items, Function<T, GeoPoint> locate) {
        this.locate = locate;
        for (T item : items) {
            GeoPoint p = locate.apply(item);
            cells.computeIfAbsent(key(cellOf(p.lat()), cellOf(p.lon())), k -> new ArrayList<>()).add(item);
        }
        this.size = items.size();
    }

    public static PointIndex<GeoPoint> ofPoints(List<GeoPoint> points) {
        return new PointIndex<>(points, p -> p);
    }

    public int size() {
        return size;
    }

    /** Items within {@code radiusMeters} of the point. */
    public List<T> within(GeoPoint center, double radiusMeters) {
        List<T> found = new ArrayList<>();
        int reach = (int) Math.ceil(radiusMeters / 250.0);
        long baseLat = cellOf(center.lat());
        long baseLon = cellOf(center.lon());
        for (long dLat = -reach; dLat <= reach; dLat++) {
            for (long dLon = -reach; dLon <= reach; dLon++) {
                List<T> cell = cells.get(key(baseLat + dLat, baseLon + dLon));
                if (cell == null) {
                    continue;
                }
                for (T item : cell) {
                    if (GeoUtils.distanceMeters(center, locate.apply(item)) <= radiusMeters) {
                        found.add(item);
                    }
                }
            }
        }
        return found;
    }

    public int countWithin(GeoPoint center, double radiusMeters) {
        return within(center, radiusMeters).size();
    }

    /** Distance to the nearest item within {@code maxMeters}, or {@code Double.POSITIVE_INFINITY}. */
    public double nearestDistance(GeoPoint center, double maxMeters) {
        double best = Double.POSITIVE_INFINITY;
        for (T item : within(center, maxMeters)) {
            best = Math.min(best, GeoUtils.distanceMeters(center, locate.apply(item)));
        }
        return best;
    }

    private static long cellOf(double degrees) {
        return (long) Math.floor(degrees / CELL_DEGREES);
    }

    private static long key(long latCell, long lonCell) {
        return latCell * 1_000_003L + lonCell;
    }
}
