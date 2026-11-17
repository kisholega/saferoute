package com.saferoute.ai.dto;

import java.util.List;

public class RouteRecommendationResponse {
    private String source;
    private String destination;
    private List<RouteOption> routes;
    private RouteOption recommendedRoute;
    private Boolean demoMode;
    private String message;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public List<RouteOption> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteOption> routes) {
        this.routes = routes;
    }

    public RouteOption getRecommendedRoute() {
        return recommendedRoute;
    }

    public void setRecommendedRoute(RouteOption recommendedRoute) {
        this.recommendedRoute = recommendedRoute;
    }

    public Boolean getDemoMode() {
        return demoMode;
    }

    public void setDemoMode(Boolean demoMode) {
        this.demoMode = demoMode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
