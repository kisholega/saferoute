package com.saferoute.ai.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Reads the travel time the user picked and returns the hour of day (0-23) used for scoring. */
public final class TravelTimeParser {

    private static final DateTimeFormatter TWELVE_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private TravelTimeParser() {
    }

    public static int hourOf(String travelTime) {
        return hourOf(travelTime, Clock.systemDefaultZone());
    }

    /**
     * Accepts "21:30", "9:30 PM", "2026-11-18T21:30", "DAY", "NIGHT", or blank (meaning now).
     *
     * @throws IllegalArgumentException for anything else
     */
    static int hourOf(String travelTime, Clock clock) {
        if (travelTime == null || travelTime.isBlank()) {
            return LocalDateTime.now(clock).getHour();
        }
        String value = travelTime.trim();
        try {
            if ("DAY".equalsIgnoreCase(value)) {
                return 9;
            }
            if ("NIGHT".equalsIgnoreCase(value)) {
                return 21;
            }
            if (value.toUpperCase(Locale.ROOT).matches("\\d{1,2}:\\d{2}\\s*[AP]M")) {
                String normalized = value.toUpperCase(Locale.ROOT).replaceAll("\\s*([AP]M)$", " $1");
                return LocalTime.parse(normalized, TWELVE_HOUR).getHour();
            }
            if (value.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
                String[] parts = value.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                if (hour <= 23 && minute <= 59) {
                    return hour;
                }
            }
            if (value.matches("\\d{4}-\\d{2}-\\d{2}T.*")) {
                return LocalDateTime.parse(value).getHour();
            }
        } catch (DateTimeParseException ex) {
            // fall through to the error below
        }
        throw new IllegalArgumentException("Travel time must look like 21:30 or 9:30 PM.");
    }
}
