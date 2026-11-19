package com.saferoute.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.saferoute.ai.IntegrationTestBase;
import com.saferoute.ai.dto.RiskPredictionRequest;
import com.saferoute.ai.dto.RiskPredictionResponse;
import com.saferoute.ai.model.RiskLevel;
import com.saferoute.ai.service.ml.ModelEvaluationService;
import com.saferoute.ai.service.ml.RiskPredictionService;

class ModelTest extends IntegrationTestBase {

    @Autowired private RiskPredictionService service;
    @Autowired private ModelEvaluationService evaluation;

    @Test
    void daytimeWellLitBusyStreetIsLowRisk() {
        RiskPredictionResponse r = service.predictRisk(new RiskPredictionRequest(11, 5, 5, 0, 1, 0.7, 0.9, 8));
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(r.safetyScore()).isGreaterThan(75);
    }

    @Test
    void lateNightDarkIsolatedStreetIsHighRisk() {
        RiskPredictionResponse r = service.predictRisk(new RiskPredictionRequest(23, 1, 1, 6, 5, 5.6, 6.0, 0));
        assertThat(r.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(r.safetyScore()).isLessThan(25);
        assertThat(r.riskFactors()).anyMatch(f -> f.contains("lighting"));
    }

    @Test
    void fastPathAgreesWithTheFullPrediction() {
        RiskPredictionRequest request = new RiskPredictionRequest(21, 3, 3, 2, 3, 1.5, 2.0, 4);
        assertThat(service.riskProbability(request)).isEqualTo(service.predictRisk(request).riskProbability());
    }

    @Test
    void moreLightingNeverMakesANightStreetLessSafe() {
        double previous = 1.0;
        for (int lighting = 1; lighting <= 5; lighting++) {
            double risk = service.riskProbability(new RiskPredictionRequest(22, lighting, 2, 2, 3, 1.5, 2.0, 3));
            assertThat(risk).isLessThanOrEqualTo(previous + 0.05);
            previous = risk;
        }
    }

    @Test
    void rejectsOutOfRangeInput() {
        assertThatThrownBy(() -> service.predictRisk(new RiskPredictionRequest(30, 3, 3, 1, 2, 1.0, 1.0, 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Time of travel");
    }

    @Test
    @SuppressWarnings("unchecked")
    void evaluationComparesThreeModelsWithGoodAccuracy() {
        Map<String, Object> metrics = evaluation.evaluateNow();
        assertThat(metrics.get("status")).isEqualTo("ready");
        List<Map<String, Object>> models = (List<Map<String, Object>>) metrics.get("models");
        assertThat(models).extracting(m -> m.get("name"))
                .containsExactly("Random Forest", "Decision Tree (J48)", "Logistic Regression");
        assertThat((Double) models.get(0).get("accuracy")).isGreaterThan(0.75);
        List<Map<String, Object>> importance = (List<Map<String, Object>>) metrics.get("importance");
        assertThat(importance).hasSize(8);
        Map<String, Object> confusion = (Map<String, Object>) metrics.get("confusion");
        assertThat((List<?>) confusion.get("labels")).hasSize(3);
    }
}
