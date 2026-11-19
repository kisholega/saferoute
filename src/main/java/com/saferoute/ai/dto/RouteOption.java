package com.saferoute.ai.dto;

import java.util.List;

import com.saferoute.ai.dto.RouteInsights.HelpPoint;
import com.saferoute.ai.dto.RouteInsights.Highlight;
import com.saferoute.ai.dto.RouteInsights.Segment;
import com.saferoute.ai.dto.RouteInsights.WeakStretch;
import com.saferoute.ai.model.RiskLevel;

/**
 * One candidate route with its safety evaluation.
 *
 * @param tags        e.g. "Recommended", "Fastest"
 * @param riskPercent 0-100, the combined risk used for the score
 * @param segments    stretches of the route, each with its own risk level (for colouring the map)
 * @param geometry    the route line as [lat, lon] pairs
 * @param dataQuality "live" (OpenStreetMap street data) or "estimated" (street data unavailable)
 * @param demoMode    true when no routing server was reachable and this is a straight-line estimate
 */
public record RouteOption(
        String routeId,
        String label,
        List<String> tags,
        double distanceKm,
        int travelTimeMinutes,
        double safetyScore,
        double riskPercent,
        RiskLevel riskLevel,
        List<String> riskFactors,
        List<ZoneHit> zonesOnRoute,
        List<Segment> segments,
        List<Highlight> highlights,
        List<HelpPoint> helpPoints,
        WeakStretch weakestStretch,
        String warning,
        String dataQuality,
        boolean demoMode,
        List<List<Double>> geometry) {
}
