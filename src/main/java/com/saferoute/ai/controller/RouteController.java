package com.saferoute.ai.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.ai.dto.RiskPredictionRequest;
import com.saferoute.ai.dto.RiskPredictionResponse;
import com.saferoute.ai.dto.RouteRecommendationRequest;
import com.saferoute.ai.dto.RouteRecommendationResponse;
import com.saferoute.ai.model.RouteSearchHistory;
import com.saferoute.ai.repository.RouteSearchHistoryRepository;
import com.saferoute.ai.service.CurrentUserService;
import com.saferoute.ai.service.ml.RiskPredictionService;
import com.saferoute.ai.service.RouteRecommendationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class RouteController {

    private final RouteRecommendationService routeRecommendationService;
    private final RiskPredictionService riskPredictionService;
    private final RouteSearchHistoryRepository historyRepository;
    private final CurrentUserService currentUserService;

    public RouteController(RouteRecommendationService routeRecommendationService,
                           RiskPredictionService riskPredictionService,
                           RouteSearchHistoryRepository historyRepository,
                           CurrentUserService currentUserService) {
        this.routeRecommendationService = routeRecommendationService;
        this.riskPredictionService = riskPredictionService;
        this.historyRepository = historyRepository;
        this.currentUserService = currentUserService;
    }

    /** Compare routes between two places and recommend the safest. */
    @PostMapping("/routes/recommend")
    public RouteRecommendationResponse recommend(@Valid @RequestBody RouteRecommendationRequest request) {
        return routeRecommendationService.recommend(request);
    }

    /** Body for "best time to leave". */
    public record TimeProfileRequest(
            @jakarta.validation.constraints.NotEmpty
            @jakarta.validation.constraints.Size(max = 20000) List<List<Double>> geometry,
            @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(23) Integer startHour,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(24) Integer hours) {
    }

    /** Safety of one route for each departure hour (default: the next 12 hours from now). */
    @PostMapping("/routes/time-profile")
    public List<RouteRecommendationService.HourScore> timeProfile(@Valid @RequestBody TimeProfileRequest request) {
        int start = request.startHour() != null ? request.startHour() : java.time.LocalTime.now().getHour();
        return routeRecommendationService.timeProfile(request.geometry(), start, request.hours() == null ? 12 : request.hours());
    }

    /** The current user's 10 most recent searches. */
    @GetMapping("/routes/history")
    public List<RouteSearchHistory> history() {
        return historyRepository.findTop10ByUserIdOrderByCreatedAtDesc(currentUserService.currentUserId());
    }

    /** Run the risk model directly on hand-entered features ("what if" analysis). */
    @PostMapping("/risk/predict")
    public RiskPredictionResponse predict(@Valid @RequestBody RiskPredictionRequest request) {
        return riskPredictionService.predictRisk(request);
    }
}
