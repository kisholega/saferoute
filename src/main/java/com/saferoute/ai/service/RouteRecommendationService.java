package com.saferoute.ai.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.saferoute.ai.client.RouteCandidate;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.dto.Place;
import com.saferoute.ai.dto.RouteOption;
import com.saferoute.ai.dto.RouteRecommendationRequest;
import com.saferoute.ai.dto.RouteRecommendationResponse;
import com.saferoute.ai.dto.ZoneHit;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.geo.GeoUtils;
import com.saferoute.ai.model.IncidentReport;
import com.saferoute.ai.model.RiskLevel;
import com.saferoute.ai.model.RouteSearchHistory;
import com.saferoute.ai.model.TravelMode;
import com.saferoute.ai.repository.RouteSearchHistoryRepository;
import com.saferoute.ai.service.RoutingService.RoutingResult;
import com.saferoute.ai.service.safety.OsmSafetyDataService;
import com.saferoute.ai.service.safety.RouteSafetyAnalyzer;
import com.saferoute.ai.service.safety.RouteSafetyAnalyzer.Analysis;
import com.saferoute.ai.service.safety.SafetyData;
import com.saferoute.ai.service.safety.SegmentFeatureExtractor;

/**
 * The core use case: find candidate routes, score each one stretch by stretch, and recommend the safest.
 *
 * <pre>
 * resolve places ─► routes (OSRM walk/car + safe detours) ─► one OpenStreetMap corridor query
 *   ─► features every 120 m (lighting, people, isolation, help, incidents, hour) ─► Random Forest
 *   ─► score, coloured stretches, highlights, help points ─► safest route recommended ─► history
 * </pre>
 */
@Service
public class RouteRecommendationService {

    static final double MIN_TRIP_METERS = 50;

    private final GeocodingService geocodingService;
    private final RoutingService routingService;
    private final OsmSafetyDataService safetyDataService;
    private final RouteSafetyAnalyzer analyzer;
    private final SafetyZoneService safetyZoneService;
    private final ReportService reportService;
    private final RouteSearchHistoryRepository historyRepository;
    private final CurrentUserService currentUserService;
    private final SafeRouteProperties properties;
    private final Clock clock;

    public RouteRecommendationService(GeocodingService geocodingService, RoutingService routingService,
                                      OsmSafetyDataService safetyDataService, RouteSafetyAnalyzer analyzer,
                                      SafetyZoneService safetyZoneService, ReportService reportService,
                                      RouteSearchHistoryRepository historyRepository,
                                      CurrentUserService currentUserService, SafeRouteProperties properties,
                                      Clock clock) {
        this.geocodingService = geocodingService;
        this.routingService = routingService;
        this.safetyDataService = safetyDataService;
        this.analyzer = analyzer;
        this.safetyZoneService = safetyZoneService;
        this.reportService = reportService;
        this.historyRepository = historyRepository;
        this.currentUserService = currentUserService;
        this.properties = properties;
        this.clock = clock;
    }

    public RouteRecommendationResponse recommend(RouteRecommendationRequest request) {
        Long userId = currentUserService.currentUserId();
        int hour = TravelTimeParser.hourOf(request.travelTime());
        TravelMode mode = request.mode() == null ? TravelMode.WALK : request.mode();
        Place source = resolve(request.source(), request.sourceLatitude(), request.sourceLongitude());
        Place destination = resolve(request.destination(), request.destinationLatitude(), request.destinationLongitude());
        validateTrip(source, destination);

        RoutingResult routing = routingService.findRoutes(source.point(), destination.point(), mode);
        List<RouteCandidate> candidates = routing.candidates();

        SafetyData data = routing.estimated()
                ? SafetyData.unavailable()
                : safetyDataService.alongRoutes(candidates.stream().map(RouteCandidate::geometry).toList());
        List<IncidentReport> reports = reportService.reportsAround(candidates);
        SegmentFeatureExtractor extractor = new SegmentFeatureExtractor(hour, data,
                safetyZoneService.getActiveZones(), reports, LocalDateTime.now(clock));

        List<Scored> scored = new ArrayList<>();
        for (RouteCandidate candidate : candidates) {
            Analysis analysis = analyzer.analyze(candidate.geometry(), extractor, data.help());
            scored.add(new Scored(candidate, analysis, safetyZoneService.zonesAlongRoute(candidate.geometry())));
        }

        Scored recommended = scored.stream()
                .max(Comparator.comparingDouble((Scored s) -> s.analysis().safetyScore())
                        .thenComparing(Comparator.comparingDouble((Scored s) -> s.candidate().durationSeconds()).reversed()))
                .orElseThrow();
        Scored fastest = scored.stream().min(Comparator.comparingDouble((Scored s) -> s.candidate().durationSeconds())).orElseThrow();

        String dataQuality = data.live() ? "live" : "estimated";
        List<RouteOption> options = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            Scored s = scored.get(i);
            options.add(toOption(s, i, scored.size(), s == recommended, s == fastest, routing.estimated(), dataQuality));
        }
        RouteOption best = options.get(scored.indexOf(recommended));
        saveHistory(userId, source, destination, best);

        return new RouteRecommendationResponse(source, destination, options, best.routeId(), mode,
                routing.estimated(), dataQuality, buildMessage(routing.estimated(), data.live(), options.size()), hour);
    }

    /** Safety score of one route for one departure hour. */
    public record HourScore(int hour, double safetyScore, RiskLevel riskLevel) {
    }

    /**
     * "Best time to leave": scores the same route for each of the next {@code hours} departure hours,
     * starting at {@code startHour}. Street data is fetched once; only the time-of-day features change.
     */
    public List<HourScore> timeProfile(List<List<Double>> geometry, int startHour, int hours) {
        currentUserService.currentUserId();
        if (geometry == null || geometry.size() < 2) {
            throw new IllegalArgumentException("A route needs at least two points.");
        }
        if (startHour < 0 || startHour > 23) {
            throw new IllegalArgumentException("Start hour must be between 0 and 23.");
        }
        int count = Math.max(1, Math.min(24, hours));
        List<GeoPoint> points = geometry.stream().map(pair -> new GeoPoint(pair.get(0), pair.get(1))).toList();
        SafetyData data = safetyDataService.alongRoutes(List.of(points));
        List<IncidentReport> reports = reportService.reportsAround(List.of(new RouteCandidate(0, 0, points)));
        LocalDateTime now = LocalDateTime.now(clock);
        List<HourScore> profile = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int hour = (startHour + i) % 24;
            SegmentFeatureExtractor extractor = new SegmentFeatureExtractor(hour, data,
                    safetyZoneService.getActiveZones(), reports, now);
            double score = analyzer.score(points, extractor);
            profile.add(new HourScore(hour, score, RiskLevel.fromSafetyScore(score)));
        }
        return profile;
    }

    private Place resolve(String text, Double latitude, Double longitude) {
        if (latitude != null && longitude != null) {
            GeoPoint point = new GeoPoint(latitude, longitude); // validates the range
            return new Place(text.trim(), text.trim(), point.lat(), point.lon(), "user");
        }
        return geocodingService.resolve(text);
    }

    private void validateTrip(Place source, Place destination) {
        double meters = GeoUtils.distanceMeters(source.point(), destination.point());
        if (meters < MIN_TRIP_METERS) {
            throw new IllegalArgumentException("The start and destination are the same place. Choose a different destination.");
        }
        double maxKm = properties.routing().maxTripKm();
        if (meters / 1000.0 > maxKm) {
            throw new IllegalArgumentException(String.format(java.util.Locale.ROOT,
                    "SafeRoute AI plans trips within a city (up to %.0f km). These places are %.0f km apart.",
                    maxKm, meters / 1000.0));
        }
    }

    private static RouteOption toOption(Scored s, int index, int total, boolean isRecommended, boolean isFastest,
                                        boolean estimatedRoute, String dataQuality) {
        List<String> tags = new ArrayList<>();
        if (isRecommended) {
            tags.add("Recommended");
        }
        if (isFastest && total > 1) {
            tags.add("Fastest");
        }
        Analysis a = s.analysis();
        RiskLevel level = RiskLevel.fromSafetyScore(a.safetyScore());
        return new RouteOption(
                estimatedRoute ? "estimate" : "route-" + (index + 1),
                estimatedRoute ? "Straight-line estimate" : "Route " + (char) ('A' + index),
                tags,
                Math.round(s.candidate().distanceMeters() / 10.0) / 100.0,
                Math.max(1, (int) Math.round(s.candidate().durationSeconds() / 60.0)),
                a.safetyScore(),
                Math.round(a.riskProbability() * 1000.0) / 10.0,
                level,
                a.factors(),
                s.zones(),
                a.segments(),
                a.highlights(),
                a.helpPoints(),
                a.weakest(),
                buildWarning(level, s, estimatedRoute),
                dataQuality,
                estimatedRoute,
                s.candidate().geometry().stream().map(GeoPoint::toLatLon).toList());
    }

    private static String buildWarning(RiskLevel level, Scored s, boolean estimatedRoute) {
        if (estimatedRoute) {
            return "Live routing is unavailable. This straight line only approximates the trip; follow real roads.";
        }
        if (!s.zones().isEmpty()) {
            ZoneHit worst = s.zones().get(0);
            return "Passes through " + worst.name() + " (" + worst.riskLevel().getLabel() + ").";
        }
        if (s.analysis().weakest() != null && level != RiskLevel.LOW) {
            return "Weakest part: " + s.analysis().weakest().description() + ".";
        }
        return switch (level) {
            case HIGH -> "Higher estimated risk. Prefer a busier, better-lit option if you can.";
            case MEDIUM -> "Moderate risk. Stay alert on the quieter stretches.";
            case LOW -> "Well lit, busy and close to help for most of the way.";
        };
    }

    private static String buildMessage(boolean estimatedRoute, boolean live, int routeCount) {
        if (estimatedRoute) {
            return "Live routing is unavailable, so this is a straight-line estimate.";
        }
        String base = routeCount == 1 ? "Only one practical route was found." : "Compared " + routeCount + " routes.";
        return live ? base + " Scored with live street data from OpenStreetMap."
                : base + " Street data was unavailable, so scores are estimated.";
    }

    private void saveHistory(Long userId, Place source, Place destination, RouteOption recommended) {
        RouteSearchHistory history = new RouteSearchHistory();
        history.setUserId(userId);
        history.setSourceLocation(truncate(source.name()));
        history.setDestinationLocation(truncate(destination.name()));
        history.setSourcePoint(source.latitude(), source.longitude());
        history.setDestinationPoint(destination.latitude(), destination.longitude());
        history.setRouteLabel(recommended.label());
        history.setSafetyScore(recommended.safetyScore());
        history.setRiskLevel(recommended.riskLevel());
        history.setTravelTimeMinutes(recommended.travelTimeMinutes());
        history.setDistanceKm(recommended.distanceKm());
        historyRepository.save(history);
    }

    private static String truncate(String value) {
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private record Scored(RouteCandidate candidate, Analysis analysis, List<ZoneHit> zones) {
    }
}
