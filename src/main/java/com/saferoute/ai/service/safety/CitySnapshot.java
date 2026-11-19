package com.saferoute.ai.service.safety;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.service.safety.SafetyData.Poi;

/**
 * A bundled OpenStreetMap snapshot of the city's street-safety data (police, hospitals, clinics,
 * pharmacies, fuel, street lamps, lit roads, shops and other active places, transit stops).
 * <p>
 * Public Overpass servers are often at capacity, so routes inside the city are scored from this
 * snapshot: instant, reliable and offline. Live Overpass queries are only used outside it.
 * Regenerate it with tools/ (see README) to refresh the data.
 */
@Component
public class CitySnapshot {

    private static final Logger log = LoggerFactory.getLogger(CitySnapshot.class);

    private final List<Poi> help = new ArrayList<>();
    private final List<GeoPoint> lamps = new ArrayList<>();
    private final List<GeoPoint> litRoads = new ArrayList<>();
    private final List<GeoPoint> transit = new ArrayList<>();
    private final List<Poi> activity = new ArrayList<>();
    private double south;
    private double west;
    private double north;
    private double east;
    private String snapshotDate = "";
    private boolean loaded;

    public CitySnapshot(ResourceLoader resourceLoader, ObjectMapper objectMapper, SafeRouteProperties properties) {
        Resource resource = resourceLoader.getResource(properties.overpass().citySnapshot());
        if (!resource.exists()) {
            log.info("No bundled city safety snapshot; live OpenStreetMap queries will be used.");
            return;
        }
        try (InputStream in = new GZIPInputStream(resource.getInputStream())) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode bbox = root.path("bbox");
            south = bbox.get(0).asDouble();
            west = bbox.get(1).asDouble();
            north = bbox.get(2).asDouble();
            east = bbox.get(3).asDouble();
            snapshotDate = root.path("snapshot").asText("");
            long id = 1;
            for (JsonNode h : root.path("help")) {
                help.add(new Poi(id++, h.get(2).asText(), h.get(3).asText(""), h.get(0).asDouble(), h.get(1).asDouble(), h.get(4).asInt() == 1));
            }
            for (JsonNode a : root.path("activity")) {
                activity.add(new Poi(id++, "PLACE", "", a.get(0).asDouble(), a.get(1).asDouble(), a.get(2).asInt() == 1));
            }
            readPoints(root.path("lamps"), lamps);
            readPoints(root.path("litRoads"), litRoads);
            readPoints(root.path("transit"), transit);
            loaded = true;
            log.info("Loaded {} street-safety snapshot ({}): {} help points, {} lamps, {} lit roads, {} active places, {} transit stops",
                    root.path("city").asText(), snapshotDate, help.size(), lamps.size(), litRoads.size(), activity.size(), transit.size());
        } catch (Exception ex) {
            log.warn("Could not read the city safety snapshot: {}", ex.getMessage());
        }
    }

    private static void readPoints(JsonNode array, List<GeoPoint> into) {
        for (JsonNode p : array) {
            into.add(new GeoPoint(p.get(0).asDouble(), p.get(1).asDouble()));
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    public String snapshotDate() {
        return snapshotDate;
    }

    public boolean covers(GeoPoint p) {
        return loaded && p.lat() >= south && p.lat() <= north && p.lon() >= west && p.lon() <= east;
    }

    /**
     * The snapshot's data inside a box (degrees). Help points get a wider margin than the rest because
     * the nearest police station or hospital can be a couple of kilometres away.
     */
    public SafetyData within(double minLat, double minLon, double maxLat, double maxLon) {
        double near = 0.004;  // ~450 m
        double wide = 0.03;   // ~3.3 km
        return new SafetyData(
                help.stream().filter(p -> inside(p.lat(), p.lon(), minLat - wide, minLon - wide, maxLat + wide, maxLon + wide)).toList(),
                lamps.stream().filter(p -> inside(p.lat(), p.lon(), minLat - near, minLon - near, maxLat + near, maxLon + near)).toList(),
                litRoads.stream().filter(p -> inside(p.lat(), p.lon(), minLat - near, minLon - near, maxLat + near, maxLon + near)).toList(),
                activity.stream().filter(p -> inside(p.lat(), p.lon(), minLat - near, minLon - near, maxLat + near, maxLon + near)).toList(),
                transit.stream().filter(p -> inside(p.lat(), p.lon(), minLat - near, minLon - near, maxLat + near, maxLon + near)).toList(),
                true);
    }

    private static boolean inside(double lat, double lon, double s, double w, double n, double e) {
        return lat >= s && lat <= n && lon >= w && lon <= e;
    }
}
