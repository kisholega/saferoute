package com.saferoute.ai.service.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.client.OverpassClient;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.exception.ExternalServiceException;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.geo.Polyline;
import com.saferoute.ai.service.safety.SafetyData.Poi;

/**
 * Collects street-level safety facts from OpenStreetMap with one Overpass "corridor" query: everything
 * within a few hundred metres of the candidate routes (and help points within 2.5 km). Results are cached
 * for 30 minutes. If OpenStreetMap cannot be reached, {@link SafetyData#unavailable()} is returned and
 * the caller falls back to estimates.
 */
@Service
public class OsmSafetyDataService {

    private static final Logger log = LoggerFactory.getLogger(OsmSafetyDataService.class);

    static final String HELP_AMENITIES = "police|hospital|clinic|pharmacy|fuel";
    static final String ACTIVITY_AMENITIES = "restaurant|cafe|fast_food|food_court|ice_cream|bank|atm|marketplace|cinema|"
            + "theatre|place_of_worship|college|school|bar|pub|bus_station|police|hospital|pharmacy|clinic|fuel|library";
    private static final Set<String> HELP_SET = Set.of(HELP_AMENITIES.split("\\|"));
    private static final Set<String> ACTIVITY_SET = Set.of(ACTIVITY_AMENITIES.split("\\|"));
    private static final long CACHE_MILLIS = 30 * 60 * 1000L;
    private static final int CACHE_SIZE = 60;

    private final OverpassClient overpassClient;
    private final CitySnapshot snapshot;
    private final boolean enabled;
    private final Map<String, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    public OsmSafetyDataService(OverpassClient overpassClient, CitySnapshot snapshot, SafeRouteProperties properties) {
        this.overpassClient = overpassClient;
        this.snapshot = snapshot;
        this.enabled = properties.overpass().enabled();
    }

    /** Safety facts along all the given routes (one network request for all of them). */
    public SafetyData alongRoutes(List<List<GeoPoint>> routes) {
        if (routes.isEmpty()) {
            return SafetyData.unavailable();
        }
        if (routes.stream().flatMap(List::stream).allMatch(snapshot::covers)) {
            double minLat = Double.MAX_VALUE, minLon = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            for (List<GeoPoint> route : routes) {
                for (GeoPoint p : route) {
                    minLat = Math.min(minLat, p.lat());
                    maxLat = Math.max(maxLat, p.lat());
                    minLon = Math.min(minLon, p.lon());
                    maxLon = Math.max(maxLon, p.lon());
                }
            }
            return snapshot.within(minLat, minLon, maxLat, maxLon);
        }
        if (!enabled) {
            return SafetyData.unavailable();
        }
        String fine = lineArgs(routes, 100, 450);
        String coarse = lineArgs(routes, 500, 120);
        String query = "[out:json][timeout:25];"
                + "(nwr[\"amenity\"~\"^(" + HELP_AMENITIES + ")$\"](around:2500," + coarse + "););out center tags;"
                + "(node[\"highway\"=\"street_lamp\"](around:80," + fine + ");"
                + "way[\"highway\"][\"lit\"=\"yes\"](around:60," + fine + "););out center tags;"
                + "(nwr[\"shop\"](around:150," + fine + ");"
                + "nwr[\"amenity\"~\"^(" + ACTIVITY_AMENITIES + ")$\"](around:150," + fine + ");"
                + "node[\"highway\"=\"bus_stop\"](around:200," + fine + ");"
                + "nwr[\"railway\"~\"^(station|halt|subway_entrance)$\"](around:300," + fine + ");"
                + "nwr[\"public_transport\"=\"station\"](around:300," + fine + "););out center tags;";
        return cachedQuery(query);
    }

    /** Help points (police, hospitals, clinics, pharmacies, fuel) within a radius of a point. */
    public SafetyData helpAround(GeoPoint point, int radiusMeters) {
        if (snapshot.covers(point)) {
            double dLat = radiusMeters / 111_000.0;
            double dLon = dLat / Math.max(0.2, Math.cos(Math.toRadians(point.lat())));
            SafetyData area = snapshot.within(point.lat() - dLat, point.lon() - dLon, point.lat() + dLat, point.lon() + dLon);
            List<Poi> inRadius = area.help().stream()
                    .filter(p -> com.saferoute.ai.geo.GeoUtils.distanceMeters(point, p.point()) <= radiusMeters).toList();
            return new SafetyData(inRadius, List.of(), List.of(), List.of(), List.of(), true);
        }
        if (!enabled) {
            return SafetyData.unavailable();
        }
        String query = String.format(Locale.ROOT,
                "[out:json][timeout:25];(nwr[\"amenity\"~\"^(%s)$\"](around:%d,%.4f,%.4f););out center tags;",
                HELP_AMENITIES, radiusMeters, point.lat(), point.lon());
        return cachedQuery(query);
    }

    private SafetyData cachedQuery(String query) {
        Cached cached = cache.get(query);
        if (cached != null && System.currentTimeMillis() - cached.at < CACHE_MILLIS) {
            return cached.data;
        }
        try {
            long started = System.currentTimeMillis();
            SafetyData data = parse(overpassClient.query(query));
            log.info("OpenStreetMap safety data: {} help points, {} lamps, {} lit roads, {} active places, {} transit stops ({} ms)",
                    data.help().size(), data.lamps().size(), data.litRoads().size(), data.activity().size(),
                    data.transit().size(), System.currentTimeMillis() - started);
            cache.put(query, new Cached(data, System.currentTimeMillis()));
            return data;
        } catch (ExternalServiceException ex) {
            log.warn("Using estimated safety features: {}", ex.getMessage());
            return SafetyData.unavailable();
        }
    }

    /** Sorts every OpenStreetMap element into the categories the scoring needs (an element can be in several). */
    static SafetyData parse(JsonNode elements) {
        List<Poi> help = new ArrayList<>();
        List<GeoPoint> lamps = new ArrayList<>();
        List<GeoPoint> litRoads = new ArrayList<>();
        List<Poi> activity = new ArrayList<>();
        List<GeoPoint> transit = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (JsonNode element : elements) {
            double lat = element.has("lat") ? element.path("lat").asDouble() : element.path("center").path("lat").asDouble(Double.NaN);
            double lon = element.has("lon") ? element.path("lon").asDouble() : element.path("center").path("lon").asDouble(Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon) || (lat == 0 && lon == 0)) {
                continue;
            }
            String key = element.path("type").asText() + element.path("id").asLong();
            if (!seen.add(key)) {
                continue; // the same element can be returned by several statements
            }
            JsonNode tags = element.path("tags");
            String amenity = tags.path("amenity").asText("");
            String highway = tags.path("highway").asText("");
            String name = tags.path("name:en").asText(tags.path("name").asText(""));
            boolean open24h = tags.path("opening_hours").asText("").contains("24/7")
                    || amenity.equals("police") || amenity.equals("hospital");
            long id = element.path("id").asLong();
            GeoPoint point = new GeoPoint(lat, lon);

            if (HELP_SET.contains(amenity)) {
                help.add(new Poi(id, amenity.toUpperCase(Locale.ROOT), name, lat, lon, open24h));
            }
            if (highway.equals("street_lamp")) {
                lamps.add(point);
            } else if ("yes".equals(tags.path("lit").asText()) && !highway.isEmpty()
                    && "way".equals(element.path("type").asText())) {
                litRoads.add(point);
            }
            boolean isTransit = highway.equals("bus_stop") || amenity.equals("bus_station")
                    || tags.has("railway") || "station".equals(tags.path("public_transport").asText());
            if (isTransit) {
                transit.add(point);
            }
            if (tags.has("shop")) {
                activity.add(new Poi(id, "SHOP", name, lat, lon, open24h));
            } else if (ACTIVITY_SET.contains(amenity)) {
                String kind = switch (amenity) {
                    case "restaurant", "cafe", "fast_food", "food_court", "ice_cream", "bar", "pub" -> "FOOD";
                    case "bank", "atm" -> "BANK";
                    default -> "PLACE";
                };
                activity.add(new Poi(id, kind, name, lat, lon, open24h));
            }
        }
        return new SafetyData(help, lamps, litRoads, activity, transit, true);
    }

    /** Overpass "around" accepts a list of lat,lon pairs and treats them as a line. */
    private static String lineArgs(List<List<GeoPoint>> routes, double stepMeters, int maxPoints) {
        List<GeoPoint> points = new ArrayList<>();
        for (List<GeoPoint> route : routes) {
            new Polyline(route).resample(stepMeters).forEach(sample -> points.add(sample.point()));
        }
        List<GeoPoint> thinned = points;
        if (points.size() > maxPoints) {
            int stride = (int) Math.ceil(points.size() / (double) maxPoints);
            thinned = new ArrayList<>();
            for (int i = 0; i < points.size(); i += stride) {
                thinned.add(points.get(i));
            }
        }
        return thinned.stream()
                .map(p -> String.format(Locale.ROOT, "%.5f,%.5f", p.lat(), p.lon()))
                .collect(Collectors.joining(","));
    }

    private record Cached(SafetyData data, long at) {
    }
}
