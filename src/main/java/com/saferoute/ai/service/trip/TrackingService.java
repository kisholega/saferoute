package com.saferoute.ai.service.trip;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.dto.TripDtos.TrackingSnapshot;
import com.saferoute.ai.exception.GoneException;
import com.saferoute.ai.exception.NotFoundException;
import com.saferoute.ai.model.Alert;
import com.saferoute.ai.model.AlertStatus;
import com.saferoute.ai.model.Trip;
import com.saferoute.ai.model.TripPoint;
import com.saferoute.ai.model.User;
import com.saferoute.ai.repository.AlertRepository;
import com.saferoute.ai.repository.TripPointRepository;
import com.saferoute.ai.repository.TripRepository;
import com.saferoute.ai.repository.UserRepository;

/**
 * The public, read-only view behind a tracking link. A token belongs either to a trip or to an SOS.
 * Links stop working a while after the trip ends or the SOS is resolved.
 */
@Service
public class TrackingService {

    private final TripRepository tripRepository;
    private final AlertRepository alertRepository;
    private final TripPointRepository pointRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Duration linkLifetime;
    private final Clock clock;

    public TrackingService(TripRepository tripRepository, AlertRepository alertRepository,
                           TripPointRepository pointRepository, UserRepository userRepository,
                           ObjectMapper objectMapper, SafeRouteProperties properties, Clock clock) {
        this.tripRepository = tripRepository;
        this.alertRepository = alertRepository;
        this.pointRepository = pointRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.linkLifetime = Duration.ofMinutes(properties.trip().trackingLinkMinutes());
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TrackingSnapshot snapshot(String token) {
        Instant now = clock.instant();
        Trip trip = tripRepository.findByShareToken(token).orElse(null);
        if (trip != null) {
            if (trip.getEndedAt() != null && now.isAfter(trip.getEndedAt().plus(linkLifetime))) {
                throw new GoneException("This tracking link has expired because the trip ended.");
            }
            User user = userRepository.findById(trip.getUserId()).orElseThrow();
            return new TrackingSnapshot("TRIP", firstName(user.getName()), user.getPhone(), trip.getStatus().name(),
                    tripStatusText(trip), trip.getOriginName(), trip.getDestinationName(),
                    read(trip.getRouteGeometry()), trip.getRouteSegments() == null ? null : read(trip.getRouteSegments()),
                    trail(pointRepository.findTop300ByTripIdOrderByRecordedAtDesc(trip.getId())),
                    trip.getLastLatitude(), trip.getLastLongitude(), trip.getLastAccuracy(), trip.getLastBattery(),
                    trip.getLastPingAt(), trip.getStartedAt(), trip.getEta(), trip.getProgress());
        }

        Alert alert = alertRepository.findByShareToken(token)
                .orElseThrow(() -> new NotFoundException("This tracking link is not valid."));
        if (alert.getResolvedAt() != null && now.isAfter(alert.getResolvedAt().plus(linkLifetime))) {
            throw new GoneException("This tracking link has expired because the alert was resolved.");
        }
        User user = userRepository.findById(alert.getUserId()).orElseThrow();
        Trip related = alert.getTripId() == null ? null : tripRepository.findById(alert.getTripId()).orElse(null);
        boolean active = alert.getStatus() == AlertStatus.ACTIVE;
        return new TrackingSnapshot("SOS", firstName(user.getName()), user.getPhone(),
                active ? "SOS" : "RESOLVED",
                active ? firstName(user.getName()) + " pressed SOS and may need help." : firstName(user.getName()) + " marked themselves safe.",
                related == null ? null : related.getOriginName(), related == null ? null : related.getDestinationName(),
                related == null ? null : read(related.getRouteGeometry()), null,
                trail(pointRepository.findTop300ByAlertIdOrderByRecordedAtDesc(alert.getId())),
                alert.getLatitude(), alert.getLongitude(), alert.getAccuracy(), alert.getBattery(),
                alert.getUpdatedAt(), alert.getCreatedAt(), null, 0);
    }

    private static String tripStatusText(Trip trip) {
        return switch (trip.getStatus()) {
            case ACTIVE -> "On the way to " + trip.getDestinationName();
            case CHECK_IN_REQUIRED -> "Being asked to confirm they are OK";
            case ALERTED -> "Needs attention: contacts have been alerted";
            case ARRIVED -> "Arrived safely at " + trip.getDestinationName();
            case ENDED -> "Trip ended";
        };
    }

    private static List<List<Double>> trail(List<TripPoint> newestFirst) {
        List<List<Double>> points = new ArrayList<>();
        for (TripPoint p : newestFirst) {
            points.add(List.of(p.getLatitude(), p.getLongitude()));
        }
        Collections.reverse(points);
        return points;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String firstName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }
}
