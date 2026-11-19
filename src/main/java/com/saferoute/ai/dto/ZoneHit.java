package com.saferoute.ai.dto;

import com.saferoute.ai.model.RiskLevel;

/** A risk zone that a route passes through. */
public record ZoneHit(Long zoneId, String name, RiskLevel riskLevel, String description) {
}
