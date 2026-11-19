package com.saferoute.ai.dto;

import java.util.List;

import com.saferoute.ai.model.RiskLevel;

/**
 * @param safetyScore     0-100, higher is safer
 * @param riskProbability 0-1, weighted probability of risk (High counts fully, Medium counts half)
 */
public record RiskPredictionResponse(
        RiskLevel riskLevel,
        double safetyScore,
        double riskProbability,
        String explanation,
        List<String> riskFactors) {
}
