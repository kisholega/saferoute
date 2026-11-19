package com.saferoute.ai.dto;

import com.saferoute.ai.model.TravelMode;

import jakarta.validation.constraints.NotBlank;

/**
 * Coordinates are optional: when the user picks a suggestion (or the map / GPS) they are sent along,
 * otherwise the typed text is looked up on the server.
 *
 * @param travelTime "HH:mm" (24h), "h:mm AM/PM", "DAY", "NIGHT", or blank for "now"
 * @param mode       WALK (default) or DRIVE (cab / auto)
 */
public record RouteRecommendationRequest(
        @NotBlank(message = "Enter a starting point.") String source,
        @NotBlank(message = "Enter a destination.") String destination,
        Double sourceLatitude,
        Double sourceLongitude,
        Double destinationLatitude,
        Double destinationLongitude,
        String travelDate,
        String travelTime,
        TravelMode mode) {
}
