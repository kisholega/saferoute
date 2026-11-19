package com.saferoute.ai.dto;

import com.saferoute.ai.model.RiskLevel;

/** Small records that explain a route's safety score. */
public final class RouteInsights {

    private RouteInsights() {
    }

    /**
     * A stretch of the route with one risk level.
     *
     * @param start index of the first geometry point of the stretch
     * @param end   index of the last geometry point of the stretch
     * @param note  why, e.g. "Poorly lit · few people around"
     */
    public record Segment(int start, int end, RiskLevel level, double risk, int lengthMeters, String note) {
    }

    /**
     * A one-line fact about the route.
     *
     * @param icon icon name used by the UI (lamp, police, store, people, alert, flag, info)
     * @param tone good, warn or bad
     */
    public record Highlight(String icon, String text, String tone) {
    }

    /** A place to get help near the route. */
    public record HelpPoint(String name, String type, double latitude, double longitude,
                            int metersFromRoute, int metersAlong, boolean open24h) {
    }

    /** The riskiest part of the route. */
    public record WeakStretch(int start, int end, int lengthMeters, RiskLevel level, String description) {
    }
}
