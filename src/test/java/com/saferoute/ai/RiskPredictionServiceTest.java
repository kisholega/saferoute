package com.saferoute.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.saferoute.ai.dto.RiskPredictionRequest;
import com.saferoute.ai.dto.RiskPredictionResponse;
import com.saferoute.ai.service.RiskPredictionService;

@SpringBootTest
class RiskPredictionServiceTest {

    @Autowired
    private RiskPredictionService riskPredictionService;

    @Test
    void shouldPredictLowRiskForSafeInput() {
        RiskPredictionRequest request = new RiskPredictionRequest();
        request.setTimeOfTravel(9);
        request.setLightingCondition(5);
        request.setCrowdDensity(5);
        request.setReportedIncidentCount(0);
        request.setRoadIsolationLevel(1);
        request.setDistanceToPoliceStation(0.7);
        request.setDistanceToHospital(0.9);
        request.setNearbyPublicPlaces(8);

        RiskPredictionResponse response = riskPredictionService.predictRisk(request);

        assertNotNull(response);
        assertNotNull(response.getRiskLevel());
        assertTrue(response.getSafetyScore() >= 0 && response.getSafetyScore() <= 100);
    }

    @Test
    void shouldIncludeRiskProbabilityInPrediction() {
        RiskPredictionRequest request = new RiskPredictionRequest();
        request.setTimeOfTravel(21);
        request.setLightingCondition(2);
        request.setCrowdDensity(3);
        request.setReportedIncidentCount(3);
        request.setRoadIsolationLevel(4);
        request.setDistanceToPoliceStation(2.5);
        request.setDistanceToHospital(3.1);
        request.setNearbyPublicPlaces(2);

        RiskPredictionResponse response = riskPredictionService.predictRisk(request);

        assertNotNull(response.getRiskProbability());
        assertTrue(response.getRiskProbability() >= 0.0 && response.getRiskProbability() <= 1.0);
        assertTrue(response.getSafetyScore() >= 0 && response.getSafetyScore() <= 100);
    }

    @Test
    void shouldRejectInvalidInput() {
        RiskPredictionRequest request = new RiskPredictionRequest();
        request.setTimeOfTravel(30);
        request.setLightingCondition(0);
        request.setCrowdDensity(3);
        request.setReportedIncidentCount(1);
        request.setRoadIsolationLevel(2);
        request.setDistanceToPoliceStation(1.0);
        request.setDistanceToHospital(1.0);
        request.setNearbyPublicPlaces(5);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> riskPredictionService.predictRisk(request));

        assertTrue(exception.getMessage().contains("Time of travel") || exception.getMessage().contains("Lighting condition"));
    }
}
