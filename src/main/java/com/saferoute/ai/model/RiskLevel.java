package com.saferoute.ai.model;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The single source of truth for risk labels. Stored in the database as LOW / MEDIUM / HIGH and sent to
 * the browser as "Low Risk" / "Medium Risk" / "High Risk".
 */
public enum RiskLevel {
    LOW("Low Risk", 0.0),
    MEDIUM("Medium Risk", 0.5),
    HIGH("High Risk", 1.0);

    private final String label;
    private final double riskWeight;

    RiskLevel(String label, double riskWeight) {
        this.label = label;
        this.riskWeight = riskWeight;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    /** How much this class contributes to the overall risk probability (0 = none, 1 = full). */
    public double getRiskWeight() {
        return riskWeight;
    }

    /** Accepts "High Risk", "HIGH", "high", and the legacy "Moderate Risk". */
    @JsonCreator
    public static RiskLevel fromLabel(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Risk level is required.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("low")) {
            return LOW;
        }
        if (normalized.startsWith("medium") || normalized.startsWith("moderate")) {
            return MEDIUM;
        }
        if (normalized.startsWith("high")) {
            return HIGH;
        }
        throw new IllegalArgumentException("Unknown risk level: " + value);
    }

    /** Maps a 0-100 safety score onto a level, using the same bands everywhere in the app. */
    public static RiskLevel fromSafetyScore(double safetyScore) {
        if (safetyScore >= 70) {
            return LOW;
        }
        if (safetyScore >= 45) {
            return MEDIUM;
        }
        return HIGH;
    }
}
