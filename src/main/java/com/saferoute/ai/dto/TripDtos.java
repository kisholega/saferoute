package com.saferoute.ai.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.saferoute.ai.model.TravelMode;
import com.saferoute.ai.model.TripStatus;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TripDtos {

    private TripDtos() {
    }

    /** Starts a Safe Trip on a route returned by /api/routes/recommend. */
    public record StartTripRequest(
            @NotBlank @Size(max = 255) String originName,
            @NotBlank @Size(max = 255) String destinationName,
            @NotNull Double destinationLatitude,
            @NotNull Double destinationLongitude,
            @NotEmpty @Size(min = 2, max = 20000) List<List<Double>> geometry,
            JsonNode segments,
            @NotNull @DecimalMin("0.01") Double distanceKm,
            @NotNull @Min(1) Integer durationMinutes,
            Double safetyScore,
            TravelMode mode,
            Boolean notifyContacts,
            Boolean simulated) {
    }

    public record PingRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @DecimalMin("0") Double accuracy,
            @Min(0) @Max(100) Integer battery) {
    }

    /** ok=true: "I'm OK". ok=false: "I need help" (raises an SOS). */
    public record CheckInRequest(@NotNull Boolean ok) {
    }

    public record CheckIn(boolean required, String reason, Instant deadline, long secondsLeft) {
    }

    public record TripView(Long id, TripStatus status, TravelMode mode, String originName, String destinationName,
                           double destinationLatitude, double destinationLongitude, JsonNode geometry, JsonNode segments,
                           double distanceKm, int expectedMinutes, Double safetyScore, Instant startedAt, Instant eta,
                           Instant endedAt, String trackUrl, double progress, Double remainingKm, Double deviationMeters,
                           boolean offRoute, CheckIn checkIn, Double lastLatitude, Double lastLongitude, Instant lastPingAt,
                           Long alertId, boolean simulated, boolean notifyContacts) {
    }

    /** What a contact sees on the public tracking page. Contains no account details beyond first name and phone. */
    public record TrackingSnapshot(String kind, String name, String phone, String status, String statusText,
                                   String originName, String destinationName, JsonNode geometry, JsonNode segments,
                                   List<List<Double>> trail, Double latitude, Double longitude, Double accuracy,
                                   Integer battery, Instant updatedAt, Instant startedAt, Instant eta, double progress) {
    }
}
