package com.saferoute.ai.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All application-specific settings, bound from {@code saferoute.*} keys in application.properties
 * (or environment variables / the optional .env file). Every value has a default, so the app starts
 * with no configuration at all.
 */
@ConfigurationProperties(prefix = "saferoute")
public record SafeRouteProperties(
        @DefaultValue("http://localhost:8080") String publicBaseUrl,
        @DefaultValue Http http,
        @DefaultValue Geocoding geocoding,
        @DefaultValue Routing routing,
        @DefaultValue Overpass overpass,
        @DefaultValue Ml ml,
        @DefaultValue Contacts contacts,
        @DefaultValue MapSettings map,
        @DefaultValue Alerts alerts,
        @DefaultValue Trip trip) {

    /** Outgoing HTTP calls. OpenStreetMap services require an identifying User-Agent. */
    public record Http(
            @DefaultValue("SafeRouteAI/1.0 (educational project)") String userAgent,
            @DefaultValue("4000") int connectTimeoutMs,
            @DefaultValue("10000") int readTimeoutMs) {
    }

    /**
     * Place search. The built-in place index answers prefix searches instantly; Photon covers everything
     * else; Nominatim resolves free text when the user did not pick a suggestion (max 1 request/second).
     */
    public record Geocoding(
            @DefaultValue("https://nominatim.openstreetmap.org") String nominatimUrl,
            @DefaultValue("https://photon.komoot.io") String photonUrl,
            @DefaultValue("classpath:geo/bengaluru-places.json") String placeIndex,
            @DefaultValue("IN") String countryCode) {
    }

    /** OSRM servers per travel mode. The FOSSGIS servers offer real walking and driving profiles. */
    public record Routing(
            @DefaultValue("https://routing.openstreetmap.de/routed-foot") String walkUrl,
            @DefaultValue("https://routing.openstreetmap.de/routed-car") String driveUrl,
            @DefaultValue("https://router.project-osrm.org") String driveFallbackUrl,
            @DefaultValue("100") double maxTripKm,
            @DefaultValue("3") int maxRoutes) {
    }

    /** Street-level safety data from OpenStreetMap. Mirrors are tried in order. */
    public record Overpass(
            @DefaultValue({"https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter"})
            List<String> urls,
            @DefaultValue("15000") int timeoutMs,
            @DefaultValue("true") boolean enabled,
            @DefaultValue("classpath:geo/bengaluru-safety.json.gz") String citySnapshot) {
    }

    /** The model is cached in modelDir and retrained whenever the training CSV changes. */
    public record Ml(
            @DefaultValue("classpath:ml/safety_risk_training.csv") String trainingData,
            @DefaultValue("./data/ml") String modelDir,
            @DefaultValue("true") boolean evaluateOnStartup,
            @DefaultValue("10") int cvFolds) {
    }

    /** Numbers typed without a country code get this prefix, e.g. 98765 43210 becomes +919876543210. */
    public record Contacts(
            @DefaultValue("+91") String defaultCountryCode,
            @DefaultValue("5") int maxPerUser) {
    }

    public record MapSettings(
            @DefaultValue("12.9716") double defaultLatitude,
            @DefaultValue("77.5946") double defaultLongitude,
            @DefaultValue("Bengaluru") String city) {
    }

    /** Alert channels. Push (ntfy) works with no setup; email and SMS switch on when credentials are present. */
    public record Alerts(
            @DefaultValue("true") boolean pushEnabled,
            @DefaultValue("https://ntfy.sh") String ntfyUrl,
            @DefaultValue("") String mailFrom,
            @DefaultValue("") String twilioAccountSid,
            @DefaultValue("") String twilioAuthToken,
            @DefaultValue("") String twilioFromNumber,
            @DefaultValue("false") boolean synchronous) {
    }

    /** Safe Trip timings (seconds). */
    public record Trip(
            @DefaultValue("45") int offRouteSeconds,
            @DefaultValue("150") int offRouteMeters,
            @DefaultValue("60") int checkInTimeoutSeconds,
            @DefaultValue("180") int lostContactSeconds,
            @DefaultValue("60") int arrivalMeters,
            @DefaultValue("120") int trackingLinkMinutes) {
    }
}
