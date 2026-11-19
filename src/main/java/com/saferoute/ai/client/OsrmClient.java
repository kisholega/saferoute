package com.saferoute.ai.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.exception.ExternalServiceException;
import com.saferoute.ai.geo.GeoPoint;

/** Fetches real road routes from an OSRM server (one server per travel profile). */
@Component
public class OsrmClient {

    private final RestTemplate restTemplate;

    public OsrmClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * @param serverUrl    e.g. https://routing.openstreetmap.de/routed-foot
     * @param waypoints    start, optional via points, destination
     * @param alternatives ask OSRM for up to 3 alternative routes
     * @return routes, or an empty list when OSRM says no road connects the points
     * @throws ExternalServiceException when the server cannot be reached
     */
    public List<RouteCandidate> route(String serverUrl, List<GeoPoint> waypoints, boolean alternatives) {
        String coordinates = waypoints.stream()
                .map(p -> String.format(Locale.ROOT, "%.6f,%.6f", p.lon(), p.lat()))
                .collect(Collectors.joining(";"));
        URI uri = UriComponentsBuilder.fromHttpUrl(serverUrl)
                .path("/route/v1/driving/{coordinates}")
                .queryParam("alternatives", alternatives ? 3 : "false")
                .queryParam("geometries", "geojson")
                .queryParam("overview", "full")
                .queryParam("steps", "false")
                .buildAndExpand(coordinates)
                .encode()
                .toUri();

        JsonNode root;
        try {
            root = restTemplate.getForObject(uri, JsonNode.class);
        } catch (HttpClientErrorException ex) {
            String body = ex.getResponseBodyAsString();
            if (body.contains("NoRoute") || body.contains("NoSegment")) {
                return List.of();
            }
            throw new ExternalServiceException("The routing service rejected the request.", ex);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("The routing service is unavailable right now.", ex);
        }

        List<RouteCandidate> candidates = new ArrayList<>();
        if (root == null || !"Ok".equals(root.path("code").asText())) {
            return candidates;
        }
        for (JsonNode routeNode : root.path("routes")) {
            List<GeoPoint> geometry = new ArrayList<>();
            for (JsonNode point : routeNode.path("geometry").path("coordinates")) {
                if (point.size() >= 2) {
                    geometry.add(new GeoPoint(point.get(1).asDouble(), point.get(0).asDouble()));
                }
            }
            if (geometry.size() >= 2) {
                candidates.add(new RouteCandidate(routeNode.path("distance").asDouble(),
                        routeNode.path("duration").asDouble(), geometry));
            }
        }
        return candidates;
    }
}
