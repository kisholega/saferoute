package com.saferoute.ai.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.model.DeliveryChannel;
import com.saferoute.ai.service.PlaceIndex;
import com.saferoute.ai.service.alert.AlertChannels;
import com.saferoute.ai.service.alert.TrackingLinks;
import com.saferoute.ai.service.ml.ModelEvaluationService;
import com.saferoute.ai.service.ml.RiskPredictionService;

/** Settings the frontend needs at startup, alert channel status, and model information. */
@RestController
@RequestMapping("/api")
public class AppInfoController {

    /** The UI checks this to detect an out-of-date server still running old code. */
    public static final int API_VERSION = 3;

    private final SafeRouteProperties properties;
    private final RiskPredictionService riskPredictionService;
    private final ModelEvaluationService evaluationService;
    private final AlertChannels channels;
    private final TrackingLinks links;
    private final PlaceIndex placeIndex;
    private final com.saferoute.ai.service.TunnelService tunnel;

    public AppInfoController(SafeRouteProperties properties, RiskPredictionService riskPredictionService,
                             ModelEvaluationService evaluationService, AlertChannels channels, TrackingLinks links,
                             PlaceIndex placeIndex, com.saferoute.ai.service.TunnelService tunnel) {
        this.properties = properties;
        this.riskPredictionService = riskPredictionService;
        this.evaluationService = evaluationService;
        this.channels = channels;
        this.links = links;
        this.placeIndex = placeIndex;
        this.tunnel = tunnel;
    }

    @GetMapping("/app-info")
    public Map<String, Object> appInfo() {
        Map<String, Object> alertChannels = new LinkedHashMap<>();
        for (DeliveryChannel channel : DeliveryChannel.values()) {
            alertChannels.put(channel.name().toLowerCase(), channels.isConfigured(channel));
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("apiVersion", API_VERSION);
        info.put("city", properties.map().city());
        info.put("mapCenter", Map.of("latitude", properties.map().defaultLatitude(),
                "longitude", properties.map().defaultLongitude()));
        info.put("maxContacts", properties.contacts().maxPerUser());
        info.put("defaultCountryCode", properties.contacts().defaultCountryCode());
        info.put("alertChannels", alertChannels);
        info.put("pushServer", channels.pushBaseUrl());
        info.put("publicBaseUrl", links.publicBaseUrl());
        info.put("publicLinks", links.isPublic());
        info.put("tunnel", tunnel.status());
        info.put("placesIndexed", placeIndex.size());
        info.put("model", riskPredictionService.getModelInfo());
        return info;
    }

    /** Cross-validated model comparison, confusion matrix and feature importance. */
    @GetMapping("/model/metrics")
    public Map<String, Object> metrics() {
        return evaluationService.metrics();
    }
}
