package com.saferoute.ai.client;

import java.util.List;

import com.saferoute.ai.geo.GeoPoint;

/** A raw route from the routing engine, before any safety scoring. */
public record RouteCandidate(double distanceMeters, double durationSeconds, List<GeoPoint> geometry) {
}
