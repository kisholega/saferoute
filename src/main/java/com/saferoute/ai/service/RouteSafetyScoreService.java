package com.saferoute.ai.service;

import com.saferoute.ai.dto.RiskPredictionRequest;
import com.saferoute.ai.dto.RiskPredictionResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RouteSafetyScoreService {

    private final RiskPredictionService riskPredictionService;

    public RouteSafetyScoreService(RiskPredictionService riskPredictionService) {
        this.riskPredictionService = riskPredictionService;
    }

    public Map<String, Object> calculateScore(RiskPredictionRequest request, double distanceKm, int travelTimeMinutes) {
        RiskPredictionResponse prediction = riskPredictionService.predictRisk(request);
        double finalScore = prediction.getSafetyScore();
        if (distanceKm > 5.0) {
            finalScore -= 8;
        }
        if (travelTimeMinutes > 30) {
            finalScore -= 5;
        }
        finalScore = Math.max(0, Math.min(100, finalScore));

        Map<String, Object> response = new HashMap<>();
        response.put("riskLevel", prediction.getRiskLevel());
        response.put("safetyScore", Math.round(finalScore * 10.0) / 10.0);
        response.put("distanceKm", distanceKm);
        response.put("travelTimeMinutes", travelTimeMinutes);
        response.put("mainFactors", prediction.getRiskFactors());
        response.put("explanation", prediction.getExplanation());
        return response;
    }
}
