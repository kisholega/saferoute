package com.saferoute.ai.dto;

import java.util.List;

public class RiskPredictionResponse {
    private String riskLevel;
    private Double safetyScore;
    private Double riskProbability;
    private String explanation;
    private List<String> riskFactors;

    public RiskPredictionResponse() {
    }

    public RiskPredictionResponse(String riskLevel, Double safetyScore, Double riskProbability, String explanation, List<String> riskFactors) {
        this.riskLevel = riskLevel;
        this.safetyScore = safetyScore;
        this.riskProbability = riskProbability;
        this.explanation = explanation;
        this.riskFactors = riskFactors;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getSafetyScore() {
        return safetyScore;
    }

    public void setSafetyScore(Double safetyScore) {
        this.safetyScore = safetyScore;
    }

    public Double getRiskProbability() {
        return riskProbability;
    }

    public void setRiskProbability(Double riskProbability) {
        this.riskProbability = riskProbability;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<String> getRiskFactors() {
        return riskFactors;
    }

    public void setRiskFactors(List<String> riskFactors) {
        this.riskFactors = riskFactors;
    }
}
