package com.saferoute.ai.service.alert;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.saferoute.ai.model.Alert;
import com.saferoute.ai.model.AlertType;
import com.saferoute.ai.model.Trip;
import com.saferoute.ai.model.User;

/** Writes the text of every alert type. Wording is calm, specific and always says what to do. */
@Component
public class AlertMessages {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final TrackingLinks links;
    private final ZoneId zone = ZoneId.systemDefault();

    public AlertMessages(TrackingLinks links) {
        this.links = links;
    }

    /**
     * @param trip  the related trip, may be null
     * @param token tracking token (trip or SOS), may be null
     */
    public AlertMessage build(Alert alert, User user, Trip trip, String token, String reason) {
        String name = firstName(user.getName());
        String mapUrl = TrackingLinks.mapUrl(alert.getLatitude(), alert.getLongitude());
        String trackUrl = links.trackUrl(token);
        String callUrl = "tel:" + user.getPhone();
        String time = TIME.format(alert.getCreatedAt().atZone(zone));
        String where = mapUrl == null ? "Location: not available" : "Location: " + mapUrl
                + (alert.getAccuracy() == null ? "" : " (±" + Math.round(alert.getAccuracy()) + " m)");
        String battery = alert.getBattery() == null ? "" : "\nBattery: " + alert.getBattery() + "%";
        String follow = trackUrl == null ? "" : "\nFollow live: " + trackUrl;
        String destination = trip == null ? "" : trip.getDestinationName();

        return switch (alert.getType()) {
            case SOS -> new AlertMessage(AlertType.SOS,
                    "🚨 SOS from " + name,
                    name + " pressed SOS and needs help now.\n" + where + follow + battery + "\nTime: " + time
                            + "\n\nCall " + name + " at " + user.getPhone() + ". If you can't reach " + name + ", call 112.",
                    "SOS from " + name + " (SafeRoute): needs help now. " + shortWhere(mapUrl) + " Call "
                            + user.getPhone() + " or 112.",
                    mapUrl, trackUrl, callUrl, 5, List.of("rotating_light", "sos"));
            case CHECK_IN_MISSED -> new AlertMessage(AlertType.CHECK_IN_MISSED,
                    "⚠️ " + name + " missed a safety check-in",
                    name + " is on a trip" + (destination.isEmpty() ? "" : " to " + destination)
                            + " and did not answer a safety check-in" + (reason == null ? "" : " (" + reason + ")") + ".\n"
                            + where + follow + battery + "\nTime: " + time
                            + "\n\nPlease call " + name + " at " + user.getPhone() + ". If there is no answer, call 112.",
                    name + " missed a SafeRoute safety check-in. " + shortWhere(mapUrl) + " Call " + user.getPhone() + ".",
                    mapUrl, trackUrl, callUrl, 5, List.of("warning", "rotating_light"));
            case LOST_CONTACT -> new AlertMessage(AlertType.LOST_CONTACT,
                    "⚠️ Lost contact with " + name,
                    name + "'s phone stopped sharing location during a trip"
                            + (destination.isEmpty() ? "" : " to " + destination) + ".\nLast seen: "
                            + (mapUrl == null ? "unknown" : mapUrl) + " at " + time + battery + follow
                            + "\n\nPlease try calling " + name + " at " + user.getPhone() + ".",
                    "SafeRoute lost contact with " + name + ". Last seen " + shortWhere(mapUrl) + " Call " + user.getPhone() + ".",
                    mapUrl, trackUrl, callUrl, 4, List.of("warning"));
            case TRIP_STARTED -> new AlertMessage(AlertType.TRIP_STARTED,
                    "🧭 " + name + " started a Safe Trip",
                    name + " is travelling from " + trip.getOriginName() + " to " + destination
                            + ", expected around " + TIME.format(trip.getEta().atZone(zone)) + "."
                            + follow + "\n\nYou'll be told when " + name + " arrives, or if anything looks wrong.",
                    name + " started a SafeRoute trip to " + destination + ", arriving ~"
                            + TIME.format(trip.getEta().atZone(zone)) + "." + (trackUrl == null || !links.isPublic() ? "" : " " + trackUrl),
                    mapUrl, trackUrl, callUrl, 3, List.of("compass"));
            case ARRIVED -> new AlertMessage(AlertType.ARRIVED,
                    "✅ " + name + " arrived safely",
                    name + " reached " + destination + " safely at " + time + ".",
                    name + " reached " + destination + " safely (SafeRoute).",
                    null, null, null, 3, List.of("white_check_mark"));
            case ALL_CLEAR -> new AlertMessage(AlertType.ALL_CLEAR,
                    "💚 " + name + " is safe",
                    name + " marked the earlier alert as resolved and is safe now. Thank you for looking out.",
                    name + " is safe now. The SafeRoute alert is resolved.",
                    null, null, callUrl, 3, List.of("green_heart"));
            case TEST -> new AlertMessage(AlertType.TEST,
                    "🔔 Test alert from SafeRoute AI",
                    name + " added you as an emergency contact on SafeRoute AI. This is a test: no action needed. "
                            + "If " + name + " ever needs help, you'll get an alert like this with a live location.",
                    "Test from SafeRoute AI: " + name + " added you as an emergency contact. No action needed.",
                    null, null, null, 3, List.of("bell"));
        };
    }

    public static List<String> actionsFor(AlertMessage message) {
        List<String> labels = new ArrayList<>();
        if (message.mapUrl() != null) labels.add("map");
        if (message.trackUrl() != null) labels.add("track");
        if (message.callUrl() != null) labels.add("call");
        return labels;
    }

    private static String shortWhere(String mapUrl) {
        return mapUrl == null ? "" : "Location: " + mapUrl + ".";
    }

    static String firstName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed.isEmpty() ? "Your contact" : trimmed;
    }

    public Instant now() {
        return Instant.now();
    }
}
