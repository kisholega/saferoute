package com.saferoute.ai.service.trip;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.dto.TripDtos.CheckIn;
import com.saferoute.ai.dto.TripDtos.CheckInRequest;
import com.saferoute.ai.dto.TripDtos.PingRequest;
import com.saferoute.ai.dto.TripDtos.StartTripRequest;
import com.saferoute.ai.dto.TripDtos.TripView;
import com.saferoute.ai.exception.NotFoundException;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.geo.GeoUtils;
import com.saferoute.ai.geo.Polyline;
import com.saferoute.ai.model.AlertType;
import com.saferoute.ai.model.TravelMode;
import com.saferoute.ai.model.Trip;
import com.saferoute.ai.model.TripPoint;
import com.saferoute.ai.model.TripStatus;
import com.saferoute.ai.repository.TripPointRepository;
import com.saferoute.ai.repository.TripRepository;
import com.saferoute.ai.service.CurrentUserService;
import com.saferoute.ai.service.Tokens;
import com.saferoute.ai.service.alert.AlertService;
import com.saferoute.ai.service.alert.TrackingLinks;

/**
 * Safe Trip: a route being travelled with a safety net.
 * <ul>
 *   <li>The phone reports its position every few seconds. Each report updates progress, remaining
 *       distance and how far the user is from the planned route, and detects arrival.</li>
 *   <li>The {@link #runChecks(Instant) watchdog} asks the user to check in when they leave the route or
 *       take much longer than expected, and alerts contacts when a check-in is missed or the phone goes
 *       silent.</li>
 * </ul>
 * Simulated trips (for demos) use shorter timers so the whole escalation can be shown in about a minute.
 */
@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);
    private static final List<TripStatus> LIVE = List.of(TripStatus.ACTIVE, TripStatus.CHECK_IN_REQUIRED, TripStatus.ALERTED);

    private final TripRepository tripRepository;
    private final TripPointRepository pointRepository;
    private final AlertService alertService;
    private final CurrentUserService currentUserService;
    private final TrackingHub trackingHub;
    private final TrackingLinks links;
    private final ObjectMapper objectMapper;
    private final SafeRouteProperties.Trip timings;
    private final Clock clock;
    private final Map<Long, Polyline> routeCache = new ConcurrentHashMap<>();

    public TripService(TripRepository tripRepository, TripPointRepository pointRepository, AlertService alertService,
                       CurrentUserService currentUserService, TrackingHub trackingHub, TrackingLinks links,
                       ObjectMapper objectMapper, SafeRouteProperties properties, Clock clock) {
        this.tripRepository = tripRepository;
        this.pointRepository = pointRepository;
        this.alertService = alertService;
        this.currentUserService = currentUserService;
        this.trackingHub = trackingHub;
        this.links = links;
        this.objectMapper = objectMapper;
        this.timings = properties.trip();
        this.clock = clock;
    }

    // ---------------------------------------------------------------- user actions

    @Transactional
    public TripView start(StartTripRequest request) {
        Long userId = currentUserService.currentUserId();
        Instant now = clock.instant();
        // Only one live trip at a time: finish any earlier one.
        for (Trip previous : tripRepository.findByUserIdAndStatusIn(userId, LIVE)) {
            previous.setStatus(TripStatus.ENDED);
            previous.setEndedAt(now);
        }

        List<GeoPoint> geometry = new ArrayList<>();
        for (List<Double> pair : request.geometry()) {
            if (pair.size() < 2) {
                throw new IllegalArgumentException("Each route point needs a latitude and a longitude.");
            }
            geometry.add(new GeoPoint(pair.get(0), pair.get(1)));
        }

        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setStatus(TripStatus.ACTIVE);
        trip.setMode(request.mode() == null ? TravelMode.WALK : request.mode());
        trip.setOriginName(request.originName().trim());
        trip.setDestinationName(request.destinationName().trim());
        GeoPoint destination = new GeoPoint(request.destinationLatitude(), request.destinationLongitude());
        trip.setDestinationLatitude(destination.lat());
        trip.setDestinationLongitude(destination.lon());
        trip.setRouteGeometry(toJson(geometry.stream().map(GeoPoint::toLatLon).toList()));
        trip.setRouteSegments(request.segments() == null ? null : request.segments().toString());
        trip.setDistanceKm(request.distanceKm());
        trip.setExpectedMinutes(request.durationMinutes());
        trip.setSafetyScore(request.safetyScore());
        trip.setStartedAt(now);
        trip.setEta(now.plus(Duration.ofMinutes(request.durationMinutes())));
        trip.setShareToken(Tokens.random(16));
        trip.setNotifyContacts(!Boolean.FALSE.equals(request.notifyContacts()));
        trip.setSimulated(Boolean.TRUE.equals(request.simulated()));
        trip.setRemainingKm(request.distanceKm());
        GeoPoint first = geometry.get(0);
        trip.setLastLatitude(first.lat());
        trip.setLastLongitude(first.lon());
        trip.setLastPingAt(now);
        tripRepository.save(trip);

        if (trip.getNotifyContacts()) {
            alertService.raiseForTrip(trip, AlertType.TRIP_STARTED, null);
        }
        return view(trip, now);
    }

    @Transactional
    public TripView ping(Long tripId, PingRequest ping) {
        Trip trip = findOwned(tripId);
        Instant now = clock.instant();
        if (!trip.getStatus().isLive()) {
            return view(trip, now);
        }
        GeoPoint position = new GeoPoint(ping.latitude(), ping.longitude());
        trip.setLastLatitude(position.lat());
        trip.setLastLongitude(position.lon());
        trip.setLastAccuracy(ping.accuracy());
        if (ping.battery() != null) {
            trip.setLastBattery(ping.battery());
        }
        trip.setLastPingAt(now);
        pointRepository.save(new TripPoint(trip.getId(), null, position.lat(), position.lon(), now));

        Polyline route = route(trip);
        Polyline.Projection projection = route.project(position);
        double length = Math.max(1, route.lengthMeters());
        trip.setProgress(Math.min(1.0, Math.max(trip.getProgress(), projection.alongMeters() / length)));
        trip.setRemainingKm(Math.round((length - projection.alongMeters()) / 10.0) / 100.0);
        trip.setDeviationMeters((double) Math.round(projection.offRouteMeters()));

        // GPS can be off by tens of metres; don't count that as leaving the route.
        double tolerance = timings.offRouteMeters() + Math.min(ping.accuracy() == null ? 0 : ping.accuracy(), 50);
        if (projection.offRouteMeters() > tolerance) {
            if (trip.getOffRouteSince() == null) {
                trip.setOffRouteSince(now);
            }
        } else {
            trip.setOffRouteSince(null);
        }

        double toDestination = GeoUtils.distanceMeters(position,
                new GeoPoint(trip.getDestinationLatitude(), trip.getDestinationLongitude()));
        if (toDestination <= timings.arrivalMeters()) {
            arrive(trip, now);
        }
        publish(trip, now);
        return view(trip, now);
    }

    @Transactional
    public TripView checkIn(Long tripId, CheckInRequest request) {
        Trip trip = findOwned(tripId);
        Instant now = clock.instant();
        if (!trip.getStatus().isLive()) {
            return view(trip, now);
        }
        if (Boolean.TRUE.equals(request.ok())) {
            if (trip.getStatus() == TripStatus.ALERTED) {
                alertService.resolveTripAlerts(trip); // tells contacts the user is safe
            }
            trip.setStatus(TripStatus.ACTIVE);
            trip.setCheckInReason(null);
            trip.setCheckInDeadline(null);
            trip.setOffRouteSince(null);
        } else {
            trip.setStatus(TripStatus.ALERTED);
            trip.setCheckInDeadline(null);
            trip.setAlertId(alertService.raiseForTrip(trip, AlertType.SOS, "asked for help during a check-in").getId());
        }
        publish(trip, now);
        return view(trip, now);
    }

    @Transactional
    public TripView end(Long tripId) {
        Trip trip = findOwned(tripId);
        Instant now = clock.instant();
        if (trip.getStatus().isLive()) {
            trip.setStatus(TripStatus.ENDED);
            trip.setEndedAt(now);
            publish(trip, now);
        }
        routeCache.remove(trip.getId());
        return view(trip, now);
    }

    @Transactional(readOnly = true)
    public TripView get(Long tripId) {
        return view(findOwned(tripId), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<TripView> recent() {
        Instant now = clock.instant();
        return tripRepository.findTop10ByUserIdOrderByStartedAtDesc(currentUserService.currentUserId())
                .stream().map(t -> view(t, now)).toList();
    }

    @Transactional(readOnly = true)
    public TripView active() {
        Instant now = clock.instant();
        return tripRepository.findByUserIdAndStatusIn(currentUserService.currentUserId(), LIVE).stream()
                .findFirst().map(t -> view(t, now)).orElse(null);
    }

    // ---------------------------------------------------------------- watchdog

    /** Called every few seconds by {@link TripWatchdog}; public so tests can run it with a fixed clock. */
    @Transactional
    public void runChecks(Instant now) {
        for (Trip trip : tripRepository.findByStatusIn(List.of(TripStatus.ACTIVE, TripStatus.CHECK_IN_REQUIRED))) {
            try {
                check(trip, now);
            } catch (Exception ex) {
                log.warn("Watchdog check failed for trip {}: {}", trip.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void check(Trip trip, Instant now) {
        boolean demo = Boolean.TRUE.equals(trip.getSimulated());
        int offRouteSeconds = demo ? Math.min(15, timings.offRouteSeconds()) : timings.offRouteSeconds();
        int lostSeconds = demo ? Math.min(90, timings.lostContactSeconds()) : timings.lostContactSeconds();

        if (trip.getStatus() == TripStatus.CHECK_IN_REQUIRED) {
            if (trip.getCheckInDeadline() != null && now.isAfter(trip.getCheckInDeadline())) {
                trip.setStatus(TripStatus.ALERTED);
                trip.setAlertId(alertService.raiseForTrip(trip, AlertType.CHECK_IN_MISSED, trip.getCheckInReason()).getId());
                publish(trip, now);
            }
            return;
        }

        if (trip.getLastPingAt() != null && Duration.between(trip.getLastPingAt(), now).getSeconds() > lostSeconds) {
            trip.setStatus(TripStatus.ALERTED);
            trip.setAlertId(alertService.raiseForTrip(trip, AlertType.LOST_CONTACT, null).getId());
            publish(trip, now);
            return;
        }
        if (trip.getOffRouteSince() != null
                && Duration.between(trip.getOffRouteSince(), now).getSeconds() >= offRouteSeconds) {
            requireCheckIn(trip, "you left your planned route", now);
            return;
        }
        long graceMinutes = Math.max(10, Math.round(trip.getExpectedMinutes() * 0.3));
        if (!Boolean.TRUE.equals(trip.getOverdueCheckDone()) && now.isAfter(trip.getEta().plus(Duration.ofMinutes(graceMinutes)))) {
            trip.setOverdueCheckDone(true);
            requireCheckIn(trip, "you are taking longer than expected", now);
        }
    }

    private void requireCheckIn(Trip trip, String reason, Instant now) {
        boolean demo = Boolean.TRUE.equals(trip.getSimulated());
        int timeout = demo ? Math.min(30, timings.checkInTimeoutSeconds()) : timings.checkInTimeoutSeconds();
        trip.setStatus(TripStatus.CHECK_IN_REQUIRED);
        trip.setCheckInReason(reason);
        trip.setCheckInDeadline(now.plusSeconds(timeout));
        publish(trip, now);
    }

    private void arrive(Trip trip, Instant now) {
        boolean wasAlerted = trip.getStatus() == TripStatus.ALERTED;
        trip.setStatus(TripStatus.ARRIVED);
        trip.setEndedAt(now);
        trip.setProgress(1.0);
        trip.setRemainingKm(0.0);
        trip.setCheckInDeadline(null);
        if (wasAlerted) {
            alertService.resolveTripAlerts(trip);
        }
        if (Boolean.TRUE.equals(trip.getNotifyContacts())) {
            alertService.raiseForTrip(trip, AlertType.ARRIVED, null);
        }
        routeCache.remove(trip.getId());
    }

    // ---------------------------------------------------------------- helpers

    private Trip findOwned(Long tripId) {
        return tripRepository.findByIdAndUserId(tripId, currentUserService.currentUserId())
                .orElseThrow(() -> new NotFoundException("Trip not found."));
    }

    private Polyline route(Trip trip) {
        return routeCache.computeIfAbsent(trip.getId(), id -> {
            List<GeoPoint> points = new ArrayList<>();
            readJson(trip.getRouteGeometry()).forEach(pair -> points.add(new GeoPoint(pair.get(0).asDouble(), pair.get(1).asDouble())));
            return new Polyline(points);
        });
    }

    private void publish(Trip trip, Instant now) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("status", trip.getStatus().name());
        update.put("latitude", trip.getLastLatitude());
        update.put("longitude", trip.getLastLongitude());
        update.put("accuracy", trip.getLastAccuracy());
        update.put("battery", trip.getLastBattery());
        update.put("progress", trip.getProgress());
        update.put("remainingKm", trip.getRemainingKm());
        update.put("at", now.toString());
        trackingHub.publish(trip.getShareToken(), "position", update);
    }

    TripView view(Trip trip, Instant now) {
        boolean checkInRequired = trip.getStatus() == TripStatus.CHECK_IN_REQUIRED && trip.getCheckInDeadline() != null;
        long secondsLeft = checkInRequired ? Math.max(0, Duration.between(now, trip.getCheckInDeadline()).getSeconds()) : 0;
        return new TripView(trip.getId(), trip.getStatus(), trip.getMode(), trip.getOriginName(), trip.getDestinationName(),
                trip.getDestinationLatitude(), trip.getDestinationLongitude(), readJson(trip.getRouteGeometry()),
                trip.getRouteSegments() == null ? null : readJson(trip.getRouteSegments()),
                trip.getDistanceKm(), trip.getExpectedMinutes(), trip.getSafetyScore(), trip.getStartedAt(), trip.getEta(),
                trip.getEndedAt(), links.trackUrl(trip.getShareToken()), trip.getProgress(), trip.getRemainingKm(),
                trip.getDeviationMeters(), trip.getOffRouteSince() != null,
                new CheckIn(checkInRequired, trip.getCheckInReason(), trip.getCheckInDeadline(), secondsLeft),
                trip.getLastLatitude(), trip.getLastLongitude(), trip.getLastPingAt(), trip.getAlertId(),
                Boolean.TRUE.equals(trip.getSimulated()), Boolean.TRUE.equals(trip.getNotifyContacts()));
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored trip data is unreadable", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
