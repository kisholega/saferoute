package com.saferoute.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RiskPredictionRequest {

    @NotBlank(message = "Source location is required")
    private String sourceLocation;

    @NotBlank(message = "Destination location is required")
    private String destinationLocation;

    @NotNull(message = "Time of travel is required")
    private Integer timeOfTravel;

    @NotNull(message = "Lighting condition is required")
    private Integer lightingCondition;

    @NotNull(message = "Crowd density is required")
    private Integer crowdDensity;

    @NotNull(message = "Reported incident count is required")
    private Integer reportedIncidentCount;

    @NotNull(message = "Road isolation level is required")
    private Integer roadIsolationLevel;

    @NotNull(message = "Distance to police station is required")
    private Double distanceToPoliceStation;

    @NotNull(message = "Distance to hospital is required")
    private Double distanceToHospital;

    @NotNull(message = "Nearby public place availability is required")
    private Integer nearbyPublicPlaces;

    public String getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public String getDestinationLocation() {
        return destinationLocation;
    }

    public void setDestinationLocation(String destinationLocation) {
        this.destinationLocation = destinationLocation;
    }

    public Integer getTimeOfTravel() {
        return timeOfTravel;
    }

    public void setTimeOfTravel(Integer timeOfTravel) {
        this.timeOfTravel = timeOfTravel;
    }

    public Integer getLightingCondition() {
        return lightingCondition;
    }

    public void setLightingCondition(Integer lightingCondition) {
        this.lightingCondition = lightingCondition;
    }

    public Integer getCrowdDensity() {
        return crowdDensity;
    }

    public void setCrowdDensity(Integer crowdDensity) {
        this.crowdDensity = crowdDensity;
    }

    public Integer getReportedIncidentCount() {
        return reportedIncidentCount;
    }

    public void setReportedIncidentCount(Integer reportedIncidentCount) {
        this.reportedIncidentCount = reportedIncidentCount;
    }

    public Integer getRoadIsolationLevel() {
        return roadIsolationLevel;
    }

    public void setRoadIsolationLevel(Integer roadIsolationLevel) {
        this.roadIsolationLevel = roadIsolationLevel;
    }

    public Double getDistanceToPoliceStation() {
        return distanceToPoliceStation;
    }

    public void setDistanceToPoliceStation(Double distanceToPoliceStation) {
        this.distanceToPoliceStation = distanceToPoliceStation;
    }

    public Double getDistanceToHospital() {
        return distanceToHospital;
    }

    public void setDistanceToHospital(Double distanceToHospital) {
        this.distanceToHospital = distanceToHospital;
    }

    public Integer getNearbyPublicPlaces() {
        return nearbyPublicPlaces;
    }

    public void setNearbyPublicPlaces(Integer nearbyPublicPlaces) {
        this.nearbyPublicPlaces = nearbyPublicPlaces;
    }
}
