package com.saferoute.ai.service.safety;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.saferoute.ai.dto.RiskPredictionRequest;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.geo.GeoUtils;
import com.saferoute.ai.geo.PointIndex;
import com.saferoute.ai.model.IncidentReport;
import com.saferoute.ai.model.RiskLevel;
import com.saferoute.ai.model.SafetyZone;
import com.saferoute.ai.service.safety.SafetyData.Poi;

/**
 * Turns the street-level facts around one point of a route into the eight model features.
 * <p>
 * With live OpenStreetMap data the features come from what is actually nearby: street lamps and lit
 * roads (lighting), shops, eateries and transit (people around), the nearest police station and hospital,
 * community reports and flagged zones (incidents). Without live data, typical values for the time of day
 * are used and only zones and reports differ between routes.
 */
public class SegmentFeatureExtractor {

    static final double REPORT_RADIUS_M = 250;
    static final int REPORT_WINDOW_DAYS = 90;
    /** Used when no police station / hospital was found within the search radius. */
    static final double UNKNOWN_DISTANCE_KM = 3.5;

    private final int hour;
    private final TimeBand band;
    private final boolean live;
    private final PointIndex<GeoPoint> lamps;
    private final PointIndex<GeoPoint> litRoads;
    private final PointIndex<Poi> activity;
    private final PointIndex<GeoPoint> transit;
    private final PointIndex<Poi> police;
    private final PointIndex<Poi> hospitals;
    private final PointIndex<IncidentReport> reports;
    private final List<SafetyZone> zones;
    private final LocalDateTime now;

    public SegmentFeatureExtractor(int hour, SafetyData data, List<SafetyZone> zones,
                                   List<IncidentReport> reports, LocalDateTime now) {
        this.hour = hour;
        this.band = TimeBand.of(hour);
        this.live = data.live();
        this.lamps = PointIndex.ofPoints(data.lamps());
        this.litRoads = PointIndex.ofPoints(data.litRoads());
        this.activity = new PointIndex<>(data.activity(), Poi::point);
        this.transit = PointIndex.ofPoints(data.transit());
        this.police = new PointIndex<>(data.help().stream().filter(p -> p.kind().equals("POLICE")).toList(), Poi::point);
        this.hospitals = new PointIndex<>(data.help().stream()
                .filter(p -> p.kind().equals("HOSPITAL") || p.kind().equals("CLINIC")).toList(), Poi::point);
        this.reports = new PointIndex<>(reports, r -> new GeoPoint(r.getLatitude(), r.getLongitude()));
        this.zones = zones;
        this.now = now;
    }

    /** The features for one point, plus the human-readable reasons behind them. */
    public record PointFeatures(RiskPredictionRequest features, boolean dark, boolean empty, boolean isolated,
                                boolean incidents, boolean farFromPolice, boolean inZone) {
    }

    public PointFeatures extract(GeoPoint p) {
        int incidentScore = incidentScore(p);
        boolean inZone = zoneWeight(p) > 0;

        int lighting;
        int crowd;
        int isolation;
        int publicPlaces;
        double policeKm;
        double hospitalKm;

        if (live) {
            List<Poi> nearbyActivity = activity.within(p, 150);
            double activeCount = band == TimeBand.LATE_NIGHT
                    ? nearbyActivity.stream().mapToDouble(a -> a.open24h() ? 1.0 : 0.2).sum()
                    : band == TimeBand.NIGHT
                    ? nearbyActivity.stream().mapToDouble(a -> a.open24h() ? 1.0 : 0.6).sum()
                    : nearbyActivity.size();
            int lampsNear = lamps.countWithin(p, 60);
            int litNear = litRoads.countWithin(p, 80);
            int shopsGlow = activity.countWithin(p, 80);
            int transitNear = transit.countWithin(p, 200);

            lighting = band == TimeBand.DAY ? 5
                    : clamp(1 + (int) Math.round(Math.min(4, lampsNear * 0.9 + litNear * 1.3
                            + (band == TimeBand.LATE_NIGHT ? 0.15 : 0.35) * shopsGlow)), 1, 5);
            double crowdRaw = activeCount * 0.35 + transitNear * 0.9;
            crowd = clamp(1 + (int) Math.round(Math.min(4, crowdRaw * band.crowdMultiplier)), 1, 5);
            isolation = clamp(5 - (int) Math.round(Math.min(4, activeCount * 0.22 + transitNear * 0.8 + litNear * 0.5))
                    + band.isolationPenalty, 1, 5);
            publicPlaces = clamp((int) Math.round(activeCount * 0.5 + transitNear), 0, 10);
            policeKm = distanceKm(police, p);
            hospitalKm = distanceKm(hospitals, p);
            lighting = Math.max(1, lighting - (inZone ? 1 : 0));
        } else {
            // Estimated: a typical city street for the hour (we know nothing else), made worse inside flagged zones.
            int pressure = Math.min(2, zoneWeight(p));
            lighting = clamp(band.lighting + 1 - pressure, 1, 5);
            crowd = clamp(band.crowd + 1 - Math.min(1, pressure), 1, 5);
            isolation = clamp(2 + band.isolationPenalty + pressure, 1, 5);
            publicPlaces = clamp(band.publicPlaces + 1 - pressure * 2, 0, 10);
            policeKm = 1.5;
            hospitalKm = 2.0;
        }

        RiskPredictionRequest features = new RiskPredictionRequest(hour, lighting, crowd, clamp(incidentScore, 0, 10),
                isolation, round2(Math.min(20, policeKm)), round2(Math.min(20, hospitalKm)), publicPlaces);
        // Without street data, only zones and reports are facts; don't claim anything about lighting or people.
        return new PointFeatures(features,
                live && band != TimeBand.DAY && lighting <= 2,
                live && crowd <= 2,
                live && isolation >= 4,
                incidentScore >= 3 && (live || inZone || incidentScore - band.baseIncidents >= 3),
                live && policeKm > 2.0,
                inZone);
    }

    /** Flagged zones covering the point (HIGH counts 3, MEDIUM 2, LOW 1) plus nearby community reports. */
    private int incidentScore(GeoPoint p) {
        int score = band.baseIncidents + zoneWeight(p);
        double reportScore = 0;
        for (IncidentReport report : reports.within(p, REPORT_RADIUS_M)) {
            long ageDays = Duration.between(report.getCreatedAt(), now).toDays();
            if (ageDays > REPORT_WINDOW_DAYS) {
                continue;
            }
            double decay = 1.0 - ageDays / (double) REPORT_WINDOW_DAYS;
            reportScore += report.getCategory().getSeverity() * decay;
        }
        return (int) Math.round(Math.max(0, score + reportScore));
    }

    private int zoneWeight(GeoPoint p) {
        int weight = 0;
        for (SafetyZone zone : zones) {
            if (GeoUtils.distanceMeters(p, zone.centre()) <= zone.radiusMeters()) {
                weight += zone.getRiskLevel() == RiskLevel.HIGH ? 3 : zone.getRiskLevel() == RiskLevel.MEDIUM ? 2 : 1;
            }
        }
        return weight;
    }

    private static double distanceKm(PointIndex<Poi> index, GeoPoint p) {
        double meters = index.nearestDistance(p, 2500);
        return Double.isInfinite(meters) ? UNKNOWN_DISTANCE_KM : meters / 1000.0;
    }

    public boolean isLive() {
        return live;
    }

    public boolean isNight() {
        return band != TimeBand.DAY;
    }

    public List<Poi> activityNear(GeoPoint p, double radius) {
        return new ArrayList<>(activity.within(p, radius));
    }

    /** Typical conditions by time of day, from daytime (best) to late night (worst). */
    enum TimeBand {
        DAY(5, 4, 0, 0, 7, 1.0),
        EVENING(3, 3, 1, 0, 5, 0.95),
        NIGHT(2, 2, 1, 1, 3, 0.75),
        LATE_NIGHT(1, 1, 2, 1, 1, 0.45);

        final int lighting;
        final int crowd;
        final int baseIncidents;
        final int isolationPenalty;
        final int publicPlaces;
        final double crowdMultiplier;

        TimeBand(int lighting, int crowd, int baseIncidents, int isolationPenalty, int publicPlaces, double crowdMultiplier) {
            this.lighting = lighting;
            this.crowd = crowd;
            this.baseIncidents = baseIncidents;
            this.isolationPenalty = isolationPenalty;
            this.publicPlaces = publicPlaces;
            this.crowdMultiplier = crowdMultiplier;
        }

        static TimeBand of(int hour) {
            if (hour >= 7 && hour < 18) {
                return DAY;
            }
            if (hour >= 18 && hour < 20) {
                return EVENING;
            }
            if (hour >= 20 && hour < 23) {
                return NIGHT;
            }
            return LATE_NIGHT;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
