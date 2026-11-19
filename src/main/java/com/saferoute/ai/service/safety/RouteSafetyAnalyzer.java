package com.saferoute.ai.service.safety;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.saferoute.ai.dto.Place;
import com.saferoute.ai.dto.RouteInsights.HelpPoint;
import com.saferoute.ai.dto.RouteInsights.Highlight;
import com.saferoute.ai.dto.RouteInsights.Segment;
import com.saferoute.ai.dto.RouteInsights.WeakStretch;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.geo.Polyline;
import com.saferoute.ai.geo.Polyline.Sample;
import com.saferoute.ai.model.RiskLevel;
import com.saferoute.ai.service.PlaceIndex;
import com.saferoute.ai.service.ml.RiskPredictionService;
import com.saferoute.ai.service.safety.SafetyData.Poi;
import com.saferoute.ai.service.safety.SegmentFeatureExtractor.PointFeatures;

/**
 * Scores a route stretch by stretch.
 * <pre>
 * route ──resample every 120 m──► points ──features──► Random Forest ──► risk per point
 *   score = 100 × (1 − (0.6 × mean risk + 0.4 × 90th-percentile risk))
 * </pre>
 * The 90th percentile term stops one dangerous stretch from being hidden by a long safe average.
 */
@Component
public class RouteSafetyAnalyzer {

    static final double STEP_METERS = 120;
    static final double LOW_MAX_RISK = 0.30;   // matches safety score >= 70
    static final double MEDIUM_MAX_RISK = 0.55; // matches safety score >= 45

    private final RiskPredictionService model;
    private final PlaceIndex placeIndex;

    public RouteSafetyAnalyzer(RiskPredictionService model, PlaceIndex placeIndex) {
        this.model = model;
        this.placeIndex = placeIndex;
    }

    public record Analysis(double safetyScore, double riskProbability, List<Segment> segments,
                           List<Highlight> highlights, List<HelpPoint> helpPoints, WeakStretch weakest,
                           List<String> factors) {
    }

    public Analysis analyze(List<GeoPoint> geometry, SegmentFeatureExtractor extractor, List<Poi> helpPois) {
        Polyline line = new Polyline(geometry);
        List<Sample> samples = line.resample(STEP_METERS);
        int n = samples.size();
        PointFeatures[] features = new PointFeatures[n];
        double[] risk = new double[n];
        for (int i = 0; i < n; i++) {
            features[i] = extractor.extract(samples.get(i).point());
            risk[i] = model.riskProbability(features[i].features());
        }

        double mean = Arrays.stream(risk).average().orElse(0);
        double[] sorted = risk.clone();
        Arrays.sort(sorted);
        double p90 = sorted[Math.max(0, (int) Math.ceil(0.9 * n) - 1)];
        double combined = 0.6 * mean + 0.4 * p90;
        double score = Math.round((1 - combined) * 1000.0) / 10.0;

        List<Segment> segments = buildSegments(line, samples, features, risk, extractor.isLive());
        return new Analysis(score, combined, segments,
                highlights(line, samples, features, extractor, helpPois),
                helpPoints(line, helpPois),
                weakest(segments, geometry),
                factors(features, extractor.isLive()));
    }

    /** Just the 0-100 score (no explanations): used to compare many departure times quickly. */
    public double score(List<GeoPoint> geometry, SegmentFeatureExtractor extractor) {
        List<Sample> samples = new Polyline(geometry).resample(STEP_METERS);
        double[] risk = new double[samples.size()];
        for (int i = 0; i < risk.length; i++) {
            risk[i] = model.riskProbability(extractor.extract(samples.get(i).point()).features());
        }
        double mean = Arrays.stream(risk).average().orElse(0);
        double[] sorted = risk.clone();
        Arrays.sort(sorted);
        double p90 = sorted[Math.max(0, (int) Math.ceil(0.9 * risk.length) - 1)];
        return Math.round((1 - (0.6 * mean + 0.4 * p90)) * 1000.0) / 10.0;
    }

    static RiskLevel levelOf(double risk) {
        return risk <= LOW_MAX_RISK ? RiskLevel.LOW : risk <= MEDIUM_MAX_RISK ? RiskLevel.MEDIUM : RiskLevel.HIGH;
    }

    /** Neighbouring points with the same risk level become one coloured stretch of the route. */
    private static List<Segment> buildSegments(Polyline line, List<Sample> samples, PointFeatures[] f, double[] risk, boolean live) {
        List<Segment> segments = new ArrayList<>();
        int lastVertex = line.points().size() - 1;
        int runStart = 0;
        for (int i = 1; i <= samples.size(); i++) {
            boolean boundary = i == samples.size() || levelOf(risk[i]) != levelOf(risk[runStart]);
            if (!boundary) {
                continue;
            }
            int startVertex = samples.get(runStart).vertexIndex();
            int endVertex = i < samples.size() ? Math.max(startVertex + 1, samples.get(i).vertexIndex() + 1) : lastVertex;
            endVertex = Math.min(lastVertex, endVertex);
            double startAlong = samples.get(runStart).alongMeters();
            double endAlong = i < samples.size() ? samples.get(i).alongMeters() : line.lengthMeters();
            double avg = Arrays.stream(risk, runStart, i).average().orElse(0);
            segments.add(new Segment(startVertex, endVertex, levelOf(risk[runStart]),
                    Math.round(avg * 1000) / 1000.0, (int) Math.round(endAlong - startAlong),
                    describe(Arrays.copyOfRange(f, runStart, i), live)));
            runStart = i;
        }
        return segments;
    }

    /**
     * The two most important reasons that apply to at least half of a stretch, e.g. "Poorly lit · few people
     * around". Reasons are ranked by how much they matter for safety, not by how often they occur.
     */
    private static String describe(PointFeatures[] stretch, boolean live) {
        Map<String, Integer> counts = new LinkedHashMap<>(); // insertion order = importance
        counts.put("flagged area", (int) Arrays.stream(stretch).filter(PointFeatures::inZone).count());
        counts.put("reported incidents", (int) Arrays.stream(stretch).filter(PointFeatures::incidents).count());
        counts.put("poorly lit", (int) Arrays.stream(stretch).filter(PointFeatures::dark).count());
        counts.put("few people around", (int) Arrays.stream(stretch).filter(PointFeatures::empty).count());
        counts.put("isolated", (int) Arrays.stream(stretch).filter(PointFeatures::isolated).count());
        counts.put("police far away", (int) Arrays.stream(stretch).filter(PointFeatures::farFromPolice).count());
        List<String> top = counts.entrySet().stream()
                .filter(e -> e.getValue() > 0 && e.getValue() * 2 >= stretch.length)
                .limit(2).map(Map.Entry::getKey).toList();
        if (top.isEmpty()) {
            return live ? "Well lit and active" : "Estimated (no street data)";
        }
        String text = String.join(" · ", top);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static List<Highlight> highlights(Polyline line, List<Sample> samples, PointFeatures[] f,
                                              SegmentFeatureExtractor extractor, List<Poi> helpPois) {
        List<Highlight> highlights = new ArrayList<>();
        int n = samples.size();

        if (!extractor.isLive()) {
            highlights.add(new Highlight("info", "Street data was unavailable, so this score is estimated from the time of day.", "warn"));
            highlights.add(extractor.isNight()
                    ? new Highlight("moon", "Night-time journey: prefer main roads", "warn")
                    : new Highlight("sun", "Daylight journey", "good"));
            if (Arrays.stream(f).anyMatch(PointFeatures::inZone)) {
                highlights.add(new Highlight("flag", "Passes through a flagged risk area", "bad"));
            }
            return highlights;
        }

        if (extractor.isNight()) {
            long lit = Arrays.stream(f).filter(p -> p.features().lightingCondition() >= 3).count();
            int share = (int) Math.round(100.0 * lit / n);
            String tone = share >= 70 ? "good" : share >= 40 ? "warn" : "bad";
            String text = share >= 70 ? "Well lit for " + share + "% of the way"
                    : share >= 40 ? "Lit for " + share + "% of the way" : "Poorly lit for " + (100 - share) + "% of the way";
            highlights.add(new Highlight("lamp", text, tone));
        } else {
            highlights.add(new Highlight("sun", "Daylight journey", "good"));
        }

        Set<Long> policeNear = new HashSet<>();
        for (Poi poi : helpPois) {
            if (poi.kind().equals("POLICE") && line.project(poi.point()).offRouteMeters() <= 500) {
                policeNear.add(poi.osmId());
            }
        }
        if (extractor.isLive()) {
            highlights.add(policeNear.isEmpty()
                    ? new Highlight("police", "No police station within 500 m of the route", "warn")
                    : new Highlight("police", policeNear.size() + (policeNear.size() == 1 ? " police station" : " police stations")
                    + " within 500 m of the route", "good"));

            Set<Long> places = new HashSet<>();
            for (Sample s : samples) {
                extractor.activityNear(s.point(), 150).forEach(p -> places.add(p.osmId()));
            }
            if (places.size() >= 15) {
                highlights.add(new Highlight("store", "Passes " + places.size() + " shops and public places", "good"));
            } else if (places.size() < 5) {
                highlights.add(new Highlight("store", "Very few shops or public places along the way", "warn"));
            } else {
                highlights.add(new Highlight("store", "Passes " + places.size() + " shops and public places", "warn"));
            }
        }

        double longestEmpty = 0;
        double runStart = -1;
        for (int i = 0; i < n; i++) {
            boolean empty = f[i].empty() || f[i].isolated();
            if (empty && runStart < 0) {
                runStart = samples.get(i).alongMeters();
            }
            if ((!empty || i == n - 1) && runStart >= 0) {
                double end = empty ? samples.get(i).alongMeters() + STEP_METERS / 2 : samples.get(i).alongMeters();
                longestEmpty = Math.max(longestEmpty, end - runStart);
                runStart = -1;
            }
        }
        if (longestEmpty >= 300) {
            int meters = (int) (Math.round(longestEmpty / 50.0) * 50);
            highlights.add(new Highlight("people", formatMeters(meters) + " stretch with few people around",
                    longestEmpty >= 800 ? "bad" : "warn"));
        }

        if (Arrays.stream(f).anyMatch(PointFeatures::inZone)) {
            highlights.add(new Highlight("flag", "Passes through a flagged risk area", "bad"));
        } else if (Arrays.stream(f).anyMatch(PointFeatures::incidents)) {
            highlights.add(new Highlight("alert", "Incidents were reported near parts of this route", "bad"));
        }
        return highlights;
    }

    private static List<HelpPoint> helpPoints(Polyline line, List<Poi> helpPois) {
        List<HelpPoint> points = new ArrayList<>();
        for (Poi poi : helpPois) {
            Polyline.Projection projection = line.project(poi.point());
            if (projection.offRouteMeters() <= 400) {
                points.add(new HelpPoint(poi.name().isBlank() ? defaultName(poi.kind()) : poi.name(), poi.kind(),
                        poi.lat(), poi.lon(), (int) Math.round(projection.offRouteMeters()),
                        (int) Math.round(projection.alongMeters()), poi.open24h()));
            }
        }
        // Police and hospitals first, then nearest to the route; keep the list short.
        points.sort(Comparator.comparingInt((HelpPoint h) -> priority(h.type())).thenComparingInt(HelpPoint::metersFromRoute));
        List<HelpPoint> top = new ArrayList<>(points.subList(0, Math.min(8, points.size())));
        top.sort(Comparator.comparingInt(HelpPoint::metersAlong));
        return top;
    }

    private WeakStretch weakest(List<Segment> segments, List<GeoPoint> geometry) {
        Segment worst = segments.stream()
                .filter(s -> s.level() != RiskLevel.LOW)
                .max(Comparator.comparingDouble(s -> s.risk() * Math.sqrt(Math.max(1, s.lengthMeters()))))
                .orElse(null);
        if (worst == null) {
            return null;
        }
        GeoPoint middle = geometry.get((worst.start() + worst.end()) / 2);
        Place near = placeIndex.nearest(middle, 2.0);
        String where = near == null ? "" : " near " + near.name();
        return new WeakStretch(worst.start(), worst.end(), worst.lengthMeters(), worst.level(),
                formatMeters(worst.lengthMeters()) + where + ": " + worst.note().toLowerCase());
    }

    private static List<String> factors(PointFeatures[] f, boolean live) {
        int n = f.length;
        List<String> factors = new ArrayList<>();
        if (!live) {
            factors.add("Estimated from the time of day, because street data was unavailable.");
            if (Arrays.stream(f).anyMatch(PointFeatures::inZone)) factors.add("Passes through a flagged risk area.");
            return factors;
        }
        int dark = (int) Arrays.stream(f).filter(PointFeatures::dark).count();
        int empty = (int) Arrays.stream(f).filter(PointFeatures::empty).count();
        int isolated = (int) Arrays.stream(f).filter(PointFeatures::isolated).count();
        int incidents = (int) Arrays.stream(f).filter(p -> p.incidents() || p.inZone()).count();
        int farPolice = (int) Arrays.stream(f).filter(PointFeatures::farFromPolice).count();
        if (dark * 4 >= n) factors.add("Poor lighting on " + pct(dark, n) + " of the way.");
        if (empty * 4 >= n) factors.add("Few people around on " + pct(empty, n) + " of the way.");
        if (isolated * 4 >= n) factors.add("Isolated roads on " + pct(isolated, n) + " of the way.");
        if (incidents > 0) factors.add("Reported incidents or flagged areas along " + pct(incidents, n) + " of the way.");
        if (farPolice * 2 >= n) factors.add("Police help is more than 2 km away for much of the route.");
        if (factors.isEmpty()) factors.add("Good lighting, people around and help nearby for most of the route.");
        return factors;
    }

    private static String pct(int part, int total) {
        return Math.round(100.0 * part / total) + "%";
    }

    private static String formatMeters(int meters) {
        return meters >= 1000 ? String.format(java.util.Locale.ROOT, "%.1f km", meters / 1000.0) : meters + " m";
    }

    private static int priority(String kind) {
        return switch (kind) {
            case "POLICE" -> 0;
            case "HOSPITAL" -> 1;
            case "CLINIC" -> 2;
            case "PHARMACY" -> 3;
            default -> 4;
        };
    }

    private static String defaultName(String kind) {
        return switch (kind) {
            case "POLICE" -> "Police station";
            case "HOSPITAL" -> "Hospital";
            case "CLINIC" -> "Clinic";
            case "PHARMACY" -> "Pharmacy";
            case "FUEL" -> "Fuel station";
            default -> "Help point";
        };
    }
}
