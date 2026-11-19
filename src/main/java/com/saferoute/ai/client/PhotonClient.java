package com.saferoute.ai.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.dto.Place;
import com.saferoute.ai.exception.ExternalServiceException;

/**
 * Photon (photon.komoot.io): an OpenStreetMap geocoder built for search-as-you-type, plus reverse
 * geocoding. Results are biased towards a location and filtered to one country.
 */
@Component
public class PhotonClient {

    private static final int LIMIT = 10;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String countryCode;

    public PhotonClient(RestTemplate restTemplate, SafeRouteProperties properties) {
        this.restTemplate = restTemplate;
        this.baseUrl = properties.geocoding().photonUrl();
        this.countryCode = properties.geocoding().countryCode();
    }

    public List<Place> autocomplete(String query, Double biasLatitude, Double biasLongitude) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/")
                .queryParam("q", query.trim())
                .queryParam("limit", LIMIT)
                .queryParam("lang", "en");
        if (biasLatitude != null && biasLongitude != null) {
            builder.queryParam("lat", biasLatitude).queryParam("lon", biasLongitude);
        }
        JsonNode root = get(builder.encode().build().toUri(), "Place suggestions are unavailable right now.");

        List<Place> places = new ArrayList<>();
        for (JsonNode feature : root.path("features")) {
            toPlace(feature, query.trim()).ifPresent(places::add);
        }
        return places;
    }

    /** The street or place at a point, e.g. "Whitefield Main Road, Whitefield, Bengaluru". */
    public Optional<Place> reverse(double latitude, double longitude) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/reverse")
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("lang", "en")
                .encode().build().toUri();
        JsonNode root = get(uri, "Could not look up this location.");
        for (JsonNode feature : root.path("features")) {
            Optional<Place> place = toPlace(feature, "Selected point");
            if (place.isPresent()) {
                Place p = place.get();
                // Describe a position by its street or area, not by whatever shop or kiosk happens to be closest.
                JsonNode props = feature.path("properties");
                String label = "highway".equals(text(props, "osm_key")) || "place".equals(text(props, "osm_key"))
                        ? p.name()
                        : firstNonBlank(text(props, "street"), text(props, "locality"), text(props, "district"), p.name());
                // keep the exact point that was asked about, not the centre of the matched feature
                return Optional.of(new Place(label, p.displayName(), latitude, longitude, "user", p.type()));
            }
        }
        return Optional.empty();
    }

    private JsonNode get(URI uri, String errorMessage) {
        try {
            JsonNode root = restTemplate.getForObject(uri, JsonNode.class);
            return root == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : root;
        } catch (RestClientException ex) {
            throw new ExternalServiceException(errorMessage, ex);
        }
    }

    private Optional<Place> toPlace(JsonNode feature, String fallbackName) {
        JsonNode coordinates = feature.path("geometry").path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2) {
            return Optional.empty();
        }
        JsonNode props = feature.path("properties");
        String country = text(props, "countrycode");
        if (!countryCode.isBlank() && !country.isBlank() && !country.equalsIgnoreCase(countryCode)) {
            return Optional.empty();
        }
        String name = firstNonBlank(text(props, "name"), text(props, "street"), fallbackName);
        return Optional.of(new Place(name, buildDisplayName(name, props),
                coordinates.get(1).asDouble(), coordinates.get(0).asDouble(), "photon",
                prettyType(text(props, "osm_value"))));
    }

    private static String buildDisplayName(String name, JsonNode props) {
        Set<String> parts = new LinkedHashSet<>();
        parts.add(name);
        String street = text(props, "street");
        String houseNumber = text(props, "housenumber");
        if (!street.isBlank()) {
            parts.add(houseNumber.isBlank() ? street : houseNumber + " " + street);
        }
        for (String key : List.of("locality", "district", "city", "state")) {
            String value = text(props, key);
            if (!value.isBlank()) {
                parts.add(value);
            }
        }
        return String.join(", ", parts);
    }

    private static String prettyType(String osmValue) {
        if (osmValue.isBlank()) {
            return null;
        }
        String spaced = osmValue.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
