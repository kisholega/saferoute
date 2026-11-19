package com.saferoute.ai.service.alert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saferoute.ai.dto.AlertDtos.AlertView;
import com.saferoute.ai.dto.AlertDtos.DeliveryView;
import com.saferoute.ai.dto.AlertDtos.LocationReport;
import com.saferoute.ai.exception.NotFoundException;
import com.saferoute.ai.model.Alert;
import com.saferoute.ai.model.AlertDelivery;
import com.saferoute.ai.model.AlertStatus;
import com.saferoute.ai.model.AlertType;
import com.saferoute.ai.model.DeliveryChannel;
import com.saferoute.ai.model.DeliveryStatus;
import com.saferoute.ai.model.EmergencyContact;
import com.saferoute.ai.model.Trip;
import com.saferoute.ai.model.TripPoint;
import com.saferoute.ai.model.TripStatus;
import com.saferoute.ai.model.User;
import com.saferoute.ai.repository.AlertDeliveryRepository;
import com.saferoute.ai.repository.AlertRepository;
import com.saferoute.ai.repository.EmergencyContactRepository;
import com.saferoute.ai.repository.TripPointRepository;
import com.saferoute.ai.repository.TripRepository;
import com.saferoute.ai.repository.UserRepository;
import com.saferoute.ai.service.CurrentUserService;
import com.saferoute.ai.service.Tokens;
import com.saferoute.ai.service.trip.TrackingHub;

/**
 * Raises alerts and delivers them to every emergency contact over every available channel, in parallel
 * and in the background. Each attempt is recorded as an {@link AlertDelivery}, so the user sees exactly who
 * was reached and how.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    /** A second SOS within this window reuses the first one instead of messaging everyone again. */
    static final Duration SOS_REUSE_WINDOW = Duration.ofMinutes(2);

    private final AlertRepository alertRepository;
    private final AlertDeliveryRepository deliveryRepository;
    private final EmergencyContactRepository contactRepository;
    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripPointRepository pointRepository;
    private final AlertChannels channels;
    private final AlertMessages messages;
    private final TrackingLinks links;
    private final TrackingHub trackingHub;
    private final CurrentUserService currentUserService;
    private final Executor executor;
    private final Clock clock;
    private final org.springframework.transaction.support.TransactionTemplate newTransaction;

    public AlertService(AlertRepository alertRepository, AlertDeliveryRepository deliveryRepository,
                        EmergencyContactRepository contactRepository, UserRepository userRepository,
                        TripRepository tripRepository, TripPointRepository pointRepository, AlertChannels channels,
                        AlertMessages messages, TrackingLinks links, TrackingHub trackingHub,
                        CurrentUserService currentUserService, @Qualifier("alertExecutor") Executor executor, Clock clock,
                        org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.alertRepository = alertRepository;
        this.deliveryRepository = deliveryRepository;
        this.contactRepository = contactRepository;
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.pointRepository = pointRepository;
        this.channels = channels;
        this.messages = messages;
        this.links = links;
        this.trackingHub = trackingHub;
        this.currentUserService = currentUserService;
        this.executor = executor;
        this.clock = clock;
        this.newTransaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---------------------------------------------------------------- user actions

    /** The SOS button. Links to the active trip if there is one. */
    @Transactional
    public AlertView sos(LocationReport location) {
        Long userId = currentUserService.currentUserId();
        Instant now = clock.instant();
        for (Alert active : alertRepository.findByUserIdAndStatus(userId, AlertStatus.ACTIVE)) {
            if (active.getType() == AlertType.SOS && Duration.between(active.getCreatedAt(), now).compareTo(SOS_REUSE_WINDOW) < 0) {
                applyLocation(active, location, now);
                return view(active);
            }
        }
        Trip trip = tripRepository.findByUserIdAndStatusIn(userId, List.of(TripStatus.ACTIVE, TripStatus.CHECK_IN_REQUIRED, TripStatus.ALERTED))
                .stream().findFirst().orElse(null);
        Alert alert = newAlert(userId, AlertType.SOS, trip, location, now);
        alert.setShareToken(Tokens.random(16));
        alertRepository.save(alert);
        if (trip != null) {
            trip.setStatus(TripStatus.ALERTED);
            trip.setAlertId(alert.getId());
        }
        dispatch(alert, null, null);
        return view(alert);
    }

    @Transactional
    public AlertView updateLocation(Long alertId, LocationReport location) {
        Alert alert = findOwned(alertId);
        applyLocation(alert, location, clock.instant());
        return view(alert);
    }

    /** "I'm safe now": closes the alert and tells everyone. */
    @Transactional
    public AlertView resolve(Long alertId) {
        Alert alert = findOwned(alertId);
        if (alert.getStatus() == AlertStatus.ACTIVE) {
            resolveInternal(alert, clock.instant());
        }
        return view(alert);
    }

    @Transactional
    public AlertView sendTest(Long contactId) {
        Long userId = currentUserService.currentUserId();
        EmergencyContact contact = contactRepository.findByIdAndUserId(contactId, userId)
                .orElseThrow(() -> new NotFoundException("Emergency contact not found."));
        Alert alert = newAlert(userId, AlertType.TEST, null, null, clock.instant());
        alertRepository.save(alert);
        dispatch(alert, null, contact);
        return view(alert);
    }

    @Transactional(readOnly = true)
    public AlertView get(Long alertId) {
        return view(findOwned(alertId));
    }

    @Transactional(readOnly = true)
    public List<AlertView> history() {
        List<Alert> alerts = alertRepository.findTop30ByUserIdOrderByCreatedAtDesc(currentUserService.currentUserId());
        Map<Long, List<AlertDelivery>> byAlert = deliveryRepository.findByAlertIdIn(alerts.stream().map(Alert::getId).toList())
                .stream().collect(Collectors.groupingBy(AlertDelivery::getAlertId));
        return alerts.stream().map(a -> view(a, byAlert.getOrDefault(a.getId(), List.of()))).toList();
    }

    // ---------------------------------------------------------------- used by trips (no session)

    /** Raises an alert for a trip event (started, arrived, missed check-in, lost contact). */
    @Transactional
    public Alert raiseForTrip(Trip trip, AlertType type, String reason) {
        LocationReport location = new LocationReport(trip.getLastLatitude(), trip.getLastLongitude(),
                trip.getLastAccuracy(), trip.getLastBattery());
        Alert alert = newAlert(trip.getUserId(), type, trip, location, clock.instant());
        if (!type.isEmergency()) {
            alert.setStatus(AlertStatus.INFO);
        }
        alertRepository.save(alert);
        dispatch(alert, reason, null);
        return alert;
    }

    /** Resolves any open emergency alerts of a trip (the user confirmed they are OK). */
    @Transactional
    public void resolveTripAlerts(Trip trip) {
        Instant now = clock.instant();
        alertRepository.findByUserIdAndStatus(trip.getUserId(), AlertStatus.ACTIVE).stream()
                .filter(a -> trip.getId().equals(a.getTripId()))
                .forEach(a -> resolveInternal(a, now));
    }

    // ---------------------------------------------------------------- internals

    private void resolveInternal(Alert alert, Instant now) {
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(now);
        alertRepository.save(alert);
        if (alert.getTripId() != null) {
            tripRepository.findById(alert.getTripId()).ifPresent(trip -> {
                if (trip.getStatus() == TripStatus.ALERTED) {
                    trip.setStatus(TripStatus.ACTIVE);
                    trip.setCheckInReason(null);
                    trip.setCheckInDeadline(null);
                    trip.setOffRouteSince(null);
                }
            });
        }
        Alert allClear = newAlert(alert.getUserId(), AlertType.ALL_CLEAR, null, null, now);
        allClear.setStatus(AlertStatus.INFO);
        allClear.setTripId(alert.getTripId());
        alertRepository.save(allClear);
        dispatch(allClear, null, null);
        trackingHub.publish(alert.getShareToken(), "status", Map.of("status", "RESOLVED"));
    }

    private Alert newAlert(Long userId, AlertType type, Trip trip, LocationReport location, Instant now) {
        Alert alert = new Alert();
        alert.setUserId(userId);
        alert.setType(type);
        alert.setStatus(type.isEmergency() ? AlertStatus.ACTIVE : AlertStatus.INFO);
        alert.setTripId(trip == null ? null : trip.getId());
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        if (location != null) {
            alert.setLatitude(location.latitude());
            alert.setLongitude(location.longitude());
            alert.setAccuracy(location.accuracy());
            alert.setBattery(location.battery());
        }
        return alert;
    }

    private void applyLocation(Alert alert, LocationReport location, Instant now) {
        if (location == null || location.latitude() == null || location.longitude() == null) {
            return;
        }
        alert.setLatitude(location.latitude());
        alert.setLongitude(location.longitude());
        alert.setAccuracy(location.accuracy());
        if (location.battery() != null) {
            alert.setBattery(location.battery());
        }
        alert.setUpdatedAt(now);
        alertRepository.save(alert);
        pointRepository.save(new TripPoint(alert.getTripId(), alert.getId(), location.latitude(), location.longitude(), now));
        Map<String, Object> update = new java.util.LinkedHashMap<>();
        update.put("latitude", location.latitude());
        update.put("longitude", location.longitude());
        update.put("accuracy", location.accuracy());
        update.put("battery", alert.getBattery());
        update.put("at", now.toString());
        trackingHub.publish(alert.getShareToken(), "position", update);
    }

    /**
     * Records a PENDING (or SKIPPED) delivery for every contact and channel, then sends in the background.
     *
     * @param only send to this contact only (test alerts), or null for everyone
     */
    private void dispatch(Alert alert, String reason, EmergencyContact only) {
        User user = userRepository.findById(alert.getUserId()).orElseThrow();
        Trip trip = alert.getTripId() == null ? null : tripRepository.findById(alert.getTripId()).orElse(null);
        String token = alert.getShareToken() != null ? alert.getShareToken() : trip == null ? null : trip.getShareToken();
        AlertMessage message = messages.build(alert, user, trip, token, reason);
        alert.setMessage(truncate(message.title() + ": " + message.body(), 500));
        alertRepository.save(alert);

        List<EmergencyContact> contacts = only != null ? List.of(only)
                : contactRepository.findByUserIdOrderByIsPrimaryDescCreatedAtAsc(alert.getUserId());
        if (contacts.isEmpty()) {
            deliveryRepository.save(new AlertDelivery(alert.getId(), null, "No contacts", DeliveryChannel.PUSH,
                    DeliveryStatus.SKIPPED, "No emergency contacts are saved"));
            return;
        }

        List<Runnable> jobs = new ArrayList<>();
        for (EmergencyContact contact : contacts) {
            for (DeliveryChannel channel : DeliveryChannel.values()) {
                String skip = channels.skipReason(channel, contact);
                AlertDelivery delivery = deliveryRepository.save(new AlertDelivery(alert.getId(), contact.getId(),
                        contact.getName(), channel, skip == null ? DeliveryStatus.PENDING : DeliveryStatus.SKIPPED, skip));
                if (skip == null) {
                    jobs.add(() -> deliver(delivery, channel, contact, message));
                }
            }
        }
        // Start sending only after this transaction has saved the PENDING rows.
        afterCommit(() -> jobs.forEach(job -> executor.execute(job)));
    }

    private void deliver(AlertDelivery delivery, DeliveryChannel channel, EmergencyContact contact, AlertMessage message) {
        try {
            String detail = channels.send(channel, contact, message);
            delivery.setStatus(DeliveryStatus.SENT);
            delivery.setDetail(truncate(detail, 300));
        } catch (Exception ex) {
            log.warn("{} alert to {} failed: {}", channel, contact.getName(), ex.getMessage());
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setDetail(truncate(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), 300));
        }
        delivery.setUpdatedAt(clock.instant());
        // Always its own transaction: this may run after the alert's transaction has committed.
        newTransaction.executeWithoutResult(status -> deliveryRepository.save(delivery));
    }

    private static void afterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    private Alert findOwned(Long alertId) {
        return alertRepository.findByIdAndUserId(alertId, currentUserService.currentUserId())
                .orElseThrow(() -> new NotFoundException("Alert not found."));
    }

    private AlertView view(Alert alert) {
        return view(alert, deliveryRepository.findByAlertIdOrderByIdAsc(alert.getId()));
    }

    private AlertView view(Alert alert, List<AlertDelivery> deliveries) {
        String token = alert.getShareToken();
        if (token == null && alert.getTripId() != null) {
            token = tripRepository.findById(alert.getTripId()).map(Trip::getShareToken).orElse(null);
        }
        boolean inProgress = deliveries.stream().anyMatch(d -> d.getStatus() == DeliveryStatus.PENDING);
        return new AlertView(alert.getId(), alert.getType(), alert.getStatus(), alert.getTripId(), alert.getLatitude(),
                alert.getLongitude(), alert.getAccuracy(), alert.getBattery(), alert.getMessage(), alert.getCreatedAt(),
                alert.getResolvedAt(), links.trackUrl(token), deliveries.stream().map(DeliveryView::of).toList(), inProgress);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
