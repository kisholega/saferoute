package com.saferoute.ai.dto;

import java.util.List;

import com.saferoute.ai.model.TravelMode;

/**
 * @param evaluatedHour the hour of day (0-23) the routes were scored for
 * @param dataQuality   "live" when street data from OpenStreetMap was used, otherwise "estimated"
 */
public record RouteRecommendationResponse(
        Place source,
        Place destination,
        List<RouteOption> routes,
        String recommendedRouteId,
        TravelMode mode,
        boolean demoMode,
        String dataQuality,
        String message,
        int evaluatedHour) {
}
