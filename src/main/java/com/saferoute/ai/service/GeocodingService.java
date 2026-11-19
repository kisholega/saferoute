package com.saferoute.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.saferoute.ai.client.NominatimClient;
import com.saferoute.ai.client.PhotonClient;
import com.saferoute.ai.dto.Place;
import com.saferoute.ai.exception.ExternalServiceException;
import com.saferoute.ai.exception.LocationNotFoundException;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.geo.GeoUtils;

/**
 * Turns what the user types into places.
 * <p>
 * Suggestions come first from the built-in {@link PlaceIndex} (instant prefix matches for the city),
 * then from Photon for everything else. A place that genuinely does not exist gives a clear error;
 * the app never guesses a location.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final int MAX_SUGGESTIONS = 8;
    private static final Pattern COORDINATES = Pattern.compile(
            "^\\s*(-?\\d{1,2}(?:\\.\\d+)?)\\s*,\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$");

    private final PlaceIndex placeIndex;
    private final PhotonClient photonClient;
    private final NominatimClient nominatimClient;

    public GeocodingService(PlaceIndex placeIndex, PhotonClient photonClient, NominatimClient nominatimClient) {
        this.placeIndex = placeIndex;
        this.photonClient = photonClient;
        this.nominatimClient = nominatimClient;
    }

    /**
     * @param quick true = built-in index only (answers in milliseconds, used for the first keystrokes)
     */
    public List<Place> suggest(String query, Double biasLatitude, Double biasLongitude, boolean quick) {
        String cleaned = query == null ? "" : query.trim();
        if (cleaned.length() < 2) {
            throw new IllegalArgumentException("Type at least 2 characters to search.");
        }
        GeoPoint bias = biasLatitude != null && biasLongitude != null ? new GeoPoint(biasLatitude, biasLongitude) : null;
        List<Place> local = placeIndex.search(cleaned, bias, 6);
        if (quick || cleaned.length() < 3) {
            return local;
        }

        List<Place> online;
        try {
            online = photonClient.autocomplete(cleaned, biasLatitude, biasLongitude);
        } catch (ExternalServiceException ex) {
            log.warn("Photon unavailable, using the built-in place index only: {}", ex.getMessage());
            return local;
        }
        return merge(local, online);
    }

    /** Local results first, then online results that are not near-duplicates of them. */
    static List<Place> merge(List<Place> local, List<Place> online) {
        List<Place> merged = new ArrayList<>(local);
        for (Place candidate : online) {
            boolean duplicate = merged.stream().anyMatch(existing ->
                    existing.name().equalsIgnoreCase(candidate.name())
                            && GeoUtils.distanceMeters(existing.point(), candidate.point()) < 1000);
            if (!duplicate) {
                merged.add(candidate);
            }
            if (merged.size() >= MAX_SUGGESTIONS) {
                break;
            }
        }
        return merged.size() > MAX_SUGGESTIONS ? merged.subList(0, MAX_SUGGESTIONS) : merged;
    }

    /**
     * Resolves typed text to one place. Also accepts raw coordinates such as "12.97, 77.59".
     *
     * @throws LocationNotFoundException when no such place exists
     * @throws ExternalServiceException  when search is unreachable and the place index has no match
     */
    public Place resolve(String query) {
        String cleaned = query == null ? "" : query.trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Location is required.");
        }
        Optional<Place> coordinates = parseCoordinates(cleaned);
        if (coordinates.isPresent()) {
            return coordinates.get();
        }

        // A typed name that matches a known place exactly needs no network call.
        List<Place> local = placeIndex.search(cleaned, null, 1);
        if (!local.isEmpty() && local.get(0).name().equalsIgnoreCase(cleaned)) {
            return local.get(0);
        }

        try {
            return nominatimClient.search(cleaned).orElseGet(() -> {
                if (!local.isEmpty()) {
                    return local.get(0);
                }
                throw new LocationNotFoundException("We couldn't find \"" + cleaned
                        + "\". Try a more specific name (add the area or city), or pick one of the suggestions.");
            });
        } catch (ExternalServiceException ex) {
            log.warn("Nominatim unavailable: {}", ex.getMessage());
            if (!local.isEmpty()) {
                return local.get(0);
            }
            throw new ExternalServiceException(
                    "Location search is unavailable right now. Pick a suggestion or check the internet connection.", ex);
        }
    }

    /** A readable name for a map tap or GPS fix. Never fails: falls back to "Near X" or coordinates. */
    public Place reverse(double latitude, double longitude) {
        GeoPoint point = new GeoPoint(latitude, longitude);
        try {
            Optional<Place> place = photonClient.reverse(latitude, longitude);
            if (place.isPresent()) {
                return place.get();
            }
        } catch (ExternalServiceException ex) {
            log.debug("Reverse geocoding unavailable: {}", ex.getMessage());
        }
        Place nearest = placeIndex.nearest(point, 3.0);
        if (nearest != null) {
            return new Place("Near " + nearest.name(), "Near " + nearest.displayName(), latitude, longitude, "user", null);
        }
        String label = String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude);
        return new Place("Dropped pin", label, latitude, longitude, "user", null);
    }

    static Optional<Place> parseCoordinates(String text) {
        Matcher matcher = COORDINATES.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        double lat = Double.parseDouble(matcher.group(1));
        double lon = Double.parseDouble(matcher.group(2));
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return Optional.empty();
        }
        String label = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon);
        return Optional.of(new Place(label, label, lat, lon, "user"));
    }
}
