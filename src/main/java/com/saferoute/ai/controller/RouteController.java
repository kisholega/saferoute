package com.saferoute.ai.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.ai.dto.RiskPredictionRequest;
import com.saferoute.ai.dto.RiskPredictionResponse;
import com.saferoute.ai.dto.RouteRecommendationRequest;
import com.saferoute.ai.dto.RouteRecommendationResponse;
import com.saferoute.ai.repository.RouteSearchHistoryRepository;
import com.saferoute.ai.service.RiskPredictionService;
import com.saferoute.ai.service.RouteRecommendationService;
import com.saferoute.ai.service.RouteSafetyScoreService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class RouteController {

    private final RiskPredictionService riskPredictionService;
    private final RouteRecommendationService routeRecommendationService;
    private final RouteSafetyScoreService routeSafetyScoreService;
    private final RouteSearchHistoryRepository routeSearchHistoryRepository;

    public RouteController(
            RiskPredictionService riskPredictionService,
            RouteRecommendationService routeRecommendationService,
            RouteSafetyScoreService routeSafetyScoreService,
            RouteSearchHistoryRepository routeSearchHistoryRepository) {
        this.riskPredictionService = riskPredictionService;
        this.routeRecommendationService = routeRecommendationService;
        this.routeSafetyScoreService = routeSafetyScoreService;
        this.routeSearchHistoryRepository = routeSearchHistoryRepository;
    }

    @PostMapping("/risk/predict")
    public ResponseEntity<RiskPredictionResponse> predictRisk(@Valid @RequestBody RiskPredictionRequest request) {
        return ResponseEntity.ok(riskPredictionService.predictRisk(request));
    }

    @GetMapping("/locations/search")
    public ResponseEntity<List<Map<String, Object>>> searchLocations(@RequestParam String query) {
        return ResponseEntity.ok(routeRecommendationService.searchLocations(query));
    }

    @PostMapping("/routes/recommend")
    public ResponseEntity<RouteRecommendationResponse> recommend(@Valid @RequestBody RouteRecommendationRequest request) {
        return ResponseEntity.ok(routeRecommendationService.recommend(request));
    }

    @PostMapping("/routes/score")
    public ResponseEntity<Map<String, Object>> scoreRoute(@Valid @RequestBody RiskPredictionRequest request,
                                                         @RequestParam(defaultValue = "4.5") double distanceKm,
                                                         @RequestParam(defaultValue = "20") int travelTimeMinutes) {
        return ResponseEntity.ok(routeSafetyScoreService.calculateScore(request, distanceKm, travelTimeMinutes));
    }

    @GetMapping("/routes/history")
    public ResponseEntity<?> getHistory(@RequestParam(required = false, defaultValue = "1") Long userId) {
        Long normalizedUserId = (userId == null || userId <= 0) ? 1L : userId;
        return ResponseEntity.ok(routeSearchHistoryRepository.findByUserIdOrderByCreatedAtDesc(normalizedUserId));
    }
}
