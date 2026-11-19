package com.saferoute.ai.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.dto.Place;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.geo.GeoUtils;

/**
 * Instant, offline prefix search over real places in the city (suburbs, neighbourhoods, metro and
 * railway stations, malls, colleges, hospitals, parks, attractions), extracted from OpenStreetMap.
 * Typing "whi" finds Whitefield; "mg ro" finds Mahatma Gandhi Road metro via its alternate name "MG Road".
 */
@Component
public class PlaceIndex {

    private static final Logger log = LoggerFactory.getLogger(PlaceIndex.class);

    private final List<Entry> entries = new ArrayList<>();
    private final String city;

    record Entry(String name, String type, int rank, double lat, double lon, String area,
                 String normalizedName, List<String> normalizedAlts) {
    }

    public PlaceIndex(ResourceLoader resourceLoader, ObjectMapper objectMapper, SafeRouteProperties properties) {
        this.city = properties.map().city();
        Resource resource = resourceLoader.getResource(properties.geocoding().placeIndex());
        try (InputStream in = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode node : root.path("places")) {
                List<String> alts = new ArrayList<>();
                node.path("alt").forEach(alt -> alts.add(normalize(alt.asText())));
                String name = node.path("name").asText();
                entries.add(new Entry(name, node.path("type").asText(), node.path("rank").asInt(),
                        node.path("lat").asDouble(), node.path("lon").asDouble(),
                        node.hasNonNull("area") ? node.path("area").asText() : null,
                        normalize(name), alts));
            }
            log.info("Loaded {} places for instant search in {}", entries.size(), city);
        } catch (Exception ex) {
            log.warn("Place index not available ({}). Suggestions will rely on online search only.", ex.getMessage());
        }
    }

    public int size() {
        return entries.size();
    }

    /**
     * @param bias optional point; nearer places rank higher
     */
    public List<Place> search(String query, GeoPoint bias, int limit) {
        String q = normalize(query);
        if (q.length() < 2 || entries.isEmpty()) {
            return List.of();
        }
        List<String> queryWords = Arrays.asList(q.split(" "));

        List<Scored> matches = new ArrayList<>();
        for (Entry entry : entries) {
            int match = matchStrength(entry, q, queryWords);
            if (match == 0) {
                continue;
            }
            double score = match * 1000.0 + entry.rank() * 3.0;
            if (bias != null) {
                double km = GeoUtils.distanceMeters(bias, new GeoPoint(entry.lat(), entry.lon())) / 1000.0;
                score -= Math.min(km, 60) * 2.0;
            }
            if (entry.normalizedName().equals(q)) {
                score += 400; // exact name match
            }
            matches.add(new Scored(entry, score));
        }
        matches.sort(Comparator.comparingDouble(Scored::score).reversed());

        List<Place> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Scored scored : matches) {
            Entry e = scored.entry();
            if (!seen.add(e.normalizedName() + "|" + e.type())) {
                continue;
            }
            results.add(toPlace(e));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    /** The closest known place to a point, e.g. to describe a GPS position as "near Indiranagar". */
    public Place nearest(GeoPoint point, double maxKm) {
        Entry best = null;
        double bestKm = maxKm;
        for (Entry entry : entries) {
            if (entry.rank() < 50) {
                continue; // describe positions with well-known areas and landmarks only
            }
            double km = GeoUtils.distanceMeters(point, new GeoPoint(entry.lat(), entry.lon())) / 1000.0;
            if (km < bestKm) {
                bestKm = km;
                best = entry;
            }
        }
        return best == null ? null : toPlace(best);
    }

    private Place toPlace(Entry e) {
        String area = e.area() != null && !e.area().equalsIgnoreCase(e.name()) ? e.area() + ", " : "";
        return new Place(e.name(), e.name() + ", " + area + city, e.lat(), e.lon(), "index", e.type());
    }

    /** 3 = whole name starts with the query, 2 = a word or alternate name does, 1 = every query word prefixes some word. */
    private static int matchStrength(Entry entry, String q, List<String> queryWords) {
        String name = entry.normalizedName();
        if (name.startsWith(q)) {
            return 3;
        }
        if (name.contains(" " + q)) {
            return 2;
        }
        for (String alt : entry.normalizedAlts()) {
            if (alt.startsWith(q) || alt.contains(" " + q)) {
                return 2;
            }
        }
        if (queryWords.size() > 1) {
            List<String> nameWords = Arrays.asList(name.split(" "));
            boolean all = queryWords.stream().allMatch(w -> nameWords.stream().anyMatch(n -> n.startsWith(w)));
            if (all) {
                return 1;
            }
        }
        return 0;
    }

    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private record Scored(Entry entry, double score) {
    }
}
