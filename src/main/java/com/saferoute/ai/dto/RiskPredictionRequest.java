package com.saferoute.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The eight model features, matching the columns of safety_risk_training.csv. */
public record RiskPredictionRequest(
        @NotNull @Min(0) @Max(23) Integer timeOfTravel,
        @NotNull @Min(1) @Max(5) Integer lightingCondition,
        @NotNull @Min(1) @Max(5) Integer crowdDensity,
        @NotNull @Min(0) @Max(10) Integer reportedIncidentCount,
        @NotNull @Min(1) @Max(5) Integer roadIsolationLevel,
        @NotNull @DecimalMin("0.0") @DecimalMax("20.0") Double distanceToPoliceStation,
        @NotNull @DecimalMin("0.0") @DecimalMax("20.0") Double distanceToHospital,
        @NotNull @Min(0) @Max(10) Integer nearbyPublicPlaces) {
}
