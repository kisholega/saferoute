package com.saferoute.ai.client;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.dto.Place;
import com.saferoute.ai.exception.ExternalServiceException;

/**
 * Resolves a typed place name to coordinates using OpenStreetMap Nominatim.
 * <p>
 * Nominatim's usage policy allows at most 1 request per second and forbids search-as-you-type,
 * so this client is only used when the user submits a route, it throttles itself, and it caches
 * answers. Autocomplete goes through {@link PhotonClient} instead.
 */
@Component
public class NominatimClient {

    private static final long MIN_INTERVAL_MS = 1100;
    private static final int CACHE_SIZE = 300;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final Object throttleLock = new Object();
    private long lastRequestAt;
    private final Map<String, Optional<Place>> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Optional<Place>> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public NominatimClient(RestTemplate restTemplate, SafeRouteProperties properties) {
        this.restTemplate = restTemplate;
        this.baseUrl = properties.geocoding().nominatimUrl();
    }

    /** Best match for the query, or empty when the place does not exist. */
    public Optional<Place> search(String query) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        Optional<Place> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/search")
                .queryParam("format", "jsonv2")
                .queryParam("limit", 1)
                .queryParam("q", query.trim())
                .encode()
                .build()
                .toUri();

        JsonNode results;
        try {
            throttle();
            results = restTemplate.getForObject(uri, JsonNode.class);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Location search is unavailable right now.", ex);
        }

        Optional<Place> place = Optional.empty();
        if (results != null && results.isArray() && !results.isEmpty()) {
            JsonNode first = results.get(0);
            if (first.hasNonNull("lat") && first.hasNonNull("lon")) {
                String displayName = first.path("display_name").asText(query);
                String name = first.path("name").asText("");
                place = Optional.of(new Place(
                        name.isBlank() ? query.trim() : name,
                        displayName,
                        first.path("lat").asDouble(),
                        first.path("lon").asDouble(),
                        "nominatim"));
            }
        }
        cache.put(key, place);
        return place;
    }

    private void throttle() {
        synchronized (throttleLock) {
            long wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }
}
