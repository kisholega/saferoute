package com.saferoute.ai.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.ai.dto.RiskPredictionRequest;
import com.saferoute.ai.dto.RiskPredictionResponse;
import com.saferoute.ai.dto.RouteOption;
import com.saferoute.ai.dto.RouteRecommendationRequest;
import com.saferoute.ai.dto.RouteRecommendationResponse;
import com.saferoute.ai.model.RouteSearchHistory;
import com.saferoute.ai.repository.RouteSearchHistoryRepository;
import com.saferoute.ai.repository.SafetyZoneRepository;

@Service
public class RouteRecommendationService {

    private final RiskPredictionService riskPredictionService;
    private final SafetyZoneRepository safetyZoneRepository;
    private final RouteSearchHistoryRepository routeSearchHistoryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, GeocodeResult> OFFLINE_LOCATIONS = Map.ofEntries(
            Map.entry("campus gate", new GeocodeResult(12.9715, 77.5945, "Campus Gate")),
            Map.entry("city library", new GeocodeResult(12.9688, 77.5984, "City Library")),
            Map.entry("central park", new GeocodeResult(40.7829, -73.9654, "Central Park")),
            Map.entry("main station", new GeocodeResult(12.9843, 77.5839, "Main Station")),
            Map.entry("home", new GeocodeResult(12.9716, 77.5946, "Home")),
            Map.entry("office", new GeocodeResult(12.9724, 77.5923, "Office")),
            Map.entry("hospital", new GeocodeResult(12.9847, 77.5859, "Hospital")),
            Map.entry("police station", new GeocodeResult(12.9710, 77.5878, "Police Station"))
    );

    @Value("${saferoute.osrm.base-url:http://router.project-osrm.org}")
    private String osrmBaseUrl;

    public RouteRecommendationService(
            RiskPredictionService riskPredictionService,
            SafetyZoneRepository safetyZoneRepository,
            RouteSearchHistoryRepository routeSearchHistoryRepository,
            RestTemplate restTemplate) {
        this.riskPredictionService = riskPredictionService;
        this.safetyZoneRepository = safetyZoneRepository;
        this.routeSearchHistoryRepository = routeSearchHistoryRepository;
        this.restTemplate = restTemplate;
    }

    public RouteRecommendationResponse recommend(RouteRecommendationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Route request is required.");
        }
        if (request.getSource() == null || request.getSource().isBlank()) {
            throw new IllegalArgumentException("Source is required.");
        }
        if (request.getDestination() == null || request.getDestination().isBlank()) {
            throw new IllegalArgumentException("Destination is required.");
        }

        GeocodeResult source = resolveGeocodeResult(request.getSource(), request.getSourceLatitude(), request.getSourceLongitude(), request.getSourceDisplayName());
        GeocodeResult destination = resolveGeocodeResult(request.getDestination(), request.getDestinationLatitude(), request.getDestinationLongitude(), request.getDestinationDisplayName());

        List<RouteOption> routes = fetchRoutesForLocations(source, destination, request);
        if (routes == null || routes.isEmpty()) {
            throw new IllegalArgumentException("No valid route could be generated for the selected start and destination.");
        }

        RouteOption recommended = routes.stream()
                .max(Comparator.comparing(RouteOption::getSafetyScore))
                .orElse(routes.get(0));

        RouteSearchHistory history = new RouteSearchHistory();
        history.setUserId(request.getUserId() != null ? request.getUserId() : 1L);
        history.setSourceLocation(request.getSource());
        history.setDestinationLocation(request.getDestination());
        history.setRouteLabel(recommended.getLabel());
        history.setSafetyScore(recommended.getSafetyScore());
        history.setRiskLevel(recommended.getRiskLevel());
        history.setTravelTimeMinutes(recommended.getTravelTimeMinutes());
        history.setDistanceKm(recommended.getDistanceKm());
        routeSearchHistoryRepository.save(history);

        RouteRecommendationResponse response = new RouteRecommendationResponse();
        response.setSource(request.getSource());
        response.setDestination(request.getDestination());
        response.setRoutes(routes);
        response.setRecommendedRoute(recommended);
        response.setDemoMode(routes.stream().anyMatch(route -> Boolean.TRUE.equals(route.getDemoMode())));
        response.setMessage(response.getDemoMode() ? "Live routing was unavailable, so a geocoded fallback route is being shown for demonstration use only." : "Live OSRM route data and safety scoring are active for this request.");
        return response;
    }

    private List<RouteOption> fetchRoutesForLocations(GeocodeResult source, GeocodeResult destination, RouteRecommendationRequest request) {
        try {
            String routeUrl = UriComponentsBuilder.fromHttpUrl(osrmBaseUrl)
                    .path("/route/v1/driving/{lon1},{lat1};{lon2},{lat2}")
                    .queryParam("alternatives", "true")
                    .queryParam("geometries", "geojson")
                    .queryParam("overview", "full")
                    .queryParam("steps", "false")
                    .buildAndExpand(source.lon(), source.lat(), destination.lon(), destination.lat())
                    .toUriString();

            ResponseEntity<String> response = restTemplate.getForEntity(routeUrl, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode routesNode = root.get("routes");
            if (routesNode == null || !routesNode.isArray() || routesNode.isEmpty()) {
                return buildFallbackRoutes(source, destination, request, "No route geometry was returned from the routing service.");
            }

            List<RouteOption> routes = new ArrayList<>();
            for (int index = 0; index < routesNode.size(); index++) {
                JsonNode routeNode = routesNode.get(index);
                double distanceKm = routeNode.path("distance").asDouble() / 1000.0;
                int durationMinutes = Math.max(1, (int) Math.round(routeNode.path("duration").asDouble() / 60.0));
                JsonNode geometryNode = routeNode.path("geometry").path("coordinates");
                List<List<Double>> geometry = new ArrayList<>();
                if (geometryNode.isArray()) {
                    for (JsonNode pointNode : geometryNode) {
                        if (pointNode.size() >= 2) {
                            double lon = pointNode.get(0).asDouble();
                            double lat = pointNode.get(1).asDouble();
                            geometry.add(List.of(lat, lon));
                        }
                    }
                }
                if (geometry.isEmpty()) {
                    geometry = interpolateGeometry(source, destination);
                }

                RiskPredictionRequest riskRequest = buildRiskRequest(request, source, destination, distanceKm, durationMinutes, index, routesNode.size());
                RiskPredictionResponse riskResponse = riskPredictionService.predictRisk(riskRequest);

                RouteOption option = new RouteOption();
                option.setRouteId("route-" + (index + 1));
                option.setLabel(index == 0 ? "Recommended route" : "Alternative route " + (index + 1));
                option.setDistanceKm(round(distanceKm));
                option.setTravelTimeMinutes(durationMinutes);
                option.setSafetyScore(round(riskResponse.getSafetyScore()));
                option.setRiskProbability(round(riskResponse.getRiskProbability()));
                option.setRiskLevel(riskResponse.getRiskLevel());
                option.setWarning(buildWarning(riskResponse.getRiskLevel()));
                option.setDemoMode(false);
                option.setGeometry(geometry);
                routes.add(option);
            }

            return routes;
        } catch (Exception ex) {
            return buildFallbackRoutes(source, destination, request, ex.getMessage());
        }
    }

    private List<RouteOption> buildFallbackRoutes(GeocodeResult source, GeocodeResult destination, RouteRecommendationRequest request, String message) {
        List<List<Double>> geometry = interpolateGeometry(source, destination);
        RiskPredictionRequest riskRequest = buildRiskRequest(request, source, destination, 4.8, 22, 0, 1);
        RiskPredictionResponse riskResponse = riskPredictionService.predictRisk(riskRequest);

        RouteOption fallback = new RouteOption();
        fallback.setRouteId("route-1");
        fallback.setLabel("Direct route fallback");
        fallback.setDistanceKm(round(4.8));
        fallback.setTravelTimeMinutes(22);
        fallback.setSafetyScore(round(riskResponse.getSafetyScore()));
        fallback.setRiskProbability(round(riskResponse.getRiskProbability()));
        fallback.setRiskLevel(riskResponse.getRiskLevel());
        fallback.setWarning("Live routing is unavailable. This route is a geocoded fallback using the requested start and destination coordinates and should be treated as a demonstration estimate only.");
        fallback.setDemoMode(true);
        fallback.setGeometry(geometry);
        return List.of(fallback);
    }

    private RiskPredictionRequest buildRiskRequest(RouteRecommendationRequest request,
                                                 GeocodeResult source,
                                                 GeocodeResult destination,
                                                 double distanceKm,
                                                 int durationMinutes,
                                                 int index,
                                                 int totalAlternatives) {
        int hourOfDay = resolveHourOfDay(request.getTravelTime(), request.getTravelDate());
        boolean isNight = hourOfDay >= 19 || hourOfDay <= 5;

        RiskPredictionRequest riskRequest = new RiskPredictionRequest();
        riskRequest.setSourceLocation(source.displayName());
        riskRequest.setDestinationLocation(destination.displayName());
        riskRequest.setTimeOfTravel(hourOfDay);
        riskRequest.setLightingCondition(isNight ? 2 : 4);
        riskRequest.setCrowdDensity(Math.min(5, Math.max(1, (int) Math.round(4.0 - (distanceKm / 10.0) + (totalAlternatives > 1 ? 1 : 0)))));
        riskRequest.setReportedIncidentCount(Math.min(10, Math.max(0, isNight ? 2 : 1 + index)));
        riskRequest.setRoadIsolationLevel(Math.min(5, Math.max(1, (int) Math.round(2 + (distanceKm / 3.0)))));
        riskRequest.setDistanceToPoliceStation(Math.min(20.0, Math.max(0.3, distanceKm / 4.0)));
        riskRequest.setDistanceToHospital(Math.min(20.0, Math.max(0.4, distanceKm / 3.0)));
        riskRequest.setNearbyPublicPlaces(Math.min(10, Math.max(1, (int) Math.round(6 - (distanceKm / 3.0)))));
        return riskRequest;
    }

    private int resolveHourOfDay(String travelTimeValue, String travelDateValue) {
        if (travelTimeValue == null || travelTimeValue.isBlank()) {
            return LocalDateTime.now().getHour();
        }
        String normalized = travelTimeValue.trim();
        if ("DAY".equalsIgnoreCase(normalized)) {
            return 9;
        }
        if ("NIGHT".equalsIgnoreCase(normalized)) {
            return 21;
        }
        if (normalized.matches(".*[AaPp][Mm]")) {
            return LocalTime.parse(normalized, DateTimeFormatter.ofPattern("h:mm a")).getHour();
        }
        if (normalized.matches("\\d{1,2}:\\d{2}")) {
            return LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm")).getHour();
        }
        if (normalized.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
            return LocalDateTime.parse(normalized).getHour();
        }
        return LocalDateTime.now().getHour();
    }

    private List<List<Double>> interpolateGeometry(GeocodeResult source, GeocodeResult destination) {
        List<List<Double>> coordinates = new ArrayList<>();
        int steps = 5;
        for (int i = 0; i <= steps; i++) {
            double ratio = steps == 0 ? 0.0 : (double) i / steps;
            double lat = source.lat() + (destination.lat() - source.lat()) * ratio;
            double lon = source.lon() + (destination.lon() - source.lon()) * ratio;
            coordinates.add(List.of(lat, lon));
        }
        return coordinates;
    }

    public List<Map<String, Object>> searchLocations(String query) {
        String cleaned = query == null ? "" : query.trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Search query is required.");
        }

        try {
            String geocodeUrl = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", "5")
                    .queryParam("q", cleaned)
                    .queryParam("addressdetails", "1")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SafeRouteAI/1.0 (saferoute.ai.demo@example.com)");
            headers.set("Accept-Language", "en");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(geocodeUrl, HttpMethod.GET, requestEntity, String.class);
            JsonNode results = objectMapper.readTree(response.getBody());
            if (response.getStatusCode().isError() || results == null || !results.isArray() || results.isEmpty()) {
                return buildOfflineSearchResults(cleaned);
            }

            List<Map<String, Object>> matches = new ArrayList<>();
            for (JsonNode item : results) {
                if (item == null || item.path("lat").isMissingNode() || item.path("lon").isMissingNode()) {
                    continue;
                }
                Map<String, Object> match = new HashMap<>();
                match.put("displayName", item.path("display_name").asText(cleaned));
                match.put("name", item.path("name").asText(cleaned));
                match.put("address", item.path("address").toString());
                match.put("latitude", item.path("lat").asDouble());
                match.put("longitude", item.path("lon").asDouble());
                matches.add(match);
            }

            if (matches.isEmpty()) {
                return buildOfflineSearchResults(cleaned);
            }
            return matches;
        } catch (Exception ex) {
            return buildOfflineSearchResults(cleaned);
        }
    }

    private GeocodeResult resolveGeocodeResult(String locationText, Double latitudeOverride, Double longitudeOverride, String displayNameOverride) {
        if (latitudeOverride != null && longitudeOverride != null) {
            return new GeocodeResult(latitudeOverride, longitudeOverride, displayNameOverride == null || displayNameOverride.isBlank() ? locationText : displayNameOverride);
        }
        return geocodeLocation(locationText);
    }

    private GeocodeResult geocodeLocation(String locationText) {
        String cleaned = locationText == null ? "" : locationText.trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Location is required.");
        }

        try {
            String geocodeUrl = UriComponentsBuilder.fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", "5")
                    .queryParam("q", cleaned)
                    .queryParam("addressdetails", "1")
                    .build()
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SafeRouteAI/1.0 (saferoute.ai.demo@example.com)");
            headers.set("Accept-Language", "en");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(geocodeUrl, HttpMethod.GET, requestEntity, String.class);
            JsonNode results = objectMapper.readTree(response.getBody());
            if (response.getStatusCode().isError() || results == null || !results.isArray() || results.isEmpty()) {
                return resolveOfflineGeocodeResult(cleaned);
            }

            JsonNode firstMatch = results.get(0);
            double latitude = firstMatch.path("lat").asDouble();
            double longitude = firstMatch.path("lon").asDouble();
            String displayName = firstMatch.path("display_name").asText(cleaned);
            return new GeocodeResult(latitude, longitude, displayName);
        } catch (Exception ex) {
            return resolveOfflineGeocodeResult(cleaned);
        }
    }

    private List<Map<String, Object>> buildOfflineSearchResults(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, GeocodeResult> entry : OFFLINE_LOCATIONS.entrySet()) {
            String key = entry.getKey();
            if (normalized.isBlank() || key.contains(normalized) || normalized.contains(key)) {
                GeocodeResult geocodeResult = entry.getValue();
                Map<String, Object> match = new HashMap<>();
                match.put("displayName", geocodeResult.displayName());
                match.put("name", geocodeResult.displayName());
                match.put("address", "Offline location match");
                match.put("latitude", geocodeResult.lat());
                match.put("longitude", geocodeResult.lon());
                results.add(match);
            }
        }
        if (results.isEmpty()) {
            Map<String, Object> match = new HashMap<>();
            match.put("displayName", query);
            match.put("name", query);
            match.put("address", "Exact fallback coordinate unavailable");
            match.put("latitude", 12.9716);
            match.put("longitude", 77.5946);
            results.add(match);
        }
        return results;
    }

    private GeocodeResult resolveOfflineGeocodeResult(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        for (Map.Entry<String, GeocodeResult> entry : OFFLINE_LOCATIONS.entrySet()) {
            String key = entry.getKey();
            if (normalized.isBlank() || key.contains(normalized) || normalized.contains(key)) {
                return entry.getValue();
            }
        }
        return new GeocodeResult(12.9716, 77.5946, query == null || query.isBlank() ? "Current location" : query);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String buildWarning(String riskLevel) {
        String normalized = riskLevel == null ? "" : riskLevel.trim();
        if ("High Risk".equalsIgnoreCase(normalized)) {
            return "This route has a higher estimated risk. Consider using a better-lit or more populated option when possible.";
        }
        if ("Medium Risk".equalsIgnoreCase(normalized)) {
            return "This route has a moderate estimate. Continue with caution and consider alternatives.";
        }
        return "This route looks comparatively safer based on the available model inputs.";
    }

    private record GeocodeResult(double lat, double lon, String displayName) {
    }
}
