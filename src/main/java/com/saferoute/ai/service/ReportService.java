package com.saferoute.ai.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saferoute.ai.client.RouteCandidate;
import com.saferoute.ai.dto.ReportDtos.ReportRequest;
import com.saferoute.ai.dto.ReportDtos.ReportView;
import com.saferoute.ai.exception.TooManyRequestsException;
import com.saferoute.ai.geo.GeoPoint;
import com.saferoute.ai.model.IncidentReport;
import com.saferoute.ai.repository.IncidentReportRepository;

/**
 * Community safety reports. Reports from the last 90 days feed the "incidents" feature of route scoring
 * for everyone; a "felt safe here" report lowers it.
 */
@Service
@Transactional
public class ReportService {

    static final int MAX_PER_DAY = 20;
    static final int WINDOW_DAYS = 90;
    private static final double PAD_DEGREES = 0.004; // ~450 m around routes

    private final IncidentReportRepository repository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public ReportService(IncidentReportRepository repository, CurrentUserService currentUserService, Clock clock) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    public ReportView create(ReportRequest request) {
        Long userId = currentUserService.currentUserId();
        LocalDateTime now = LocalDateTime.now(clock);
        if (repository.countByUserIdAndCreatedAtAfter(userId, now.minusDays(1)) >= MAX_PER_DAY) {
            throw new TooManyRequestsException("You have made many reports today. Please try again tomorrow.");
        }
        GeoPoint point = new GeoPoint(request.latitude(), request.longitude());
        IncidentReport report = new IncidentReport();
        report.setUserId(userId);
        report.setLatitude(point.lat());
        report.setLongitude(point.lon());
        report.setCategory(request.category());
        String note = request.note() == null ? null : request.note().trim();
        report.setNote(note == null || note.isEmpty() ? null : note);
        report.setCreatedAt(now);
        return ReportView.of(repository.save(report));
    }

    @Transactional(readOnly = true)
    public List<ReportView> near(double latitude, double longitude, double radiusKm) {
        double pad = Math.min(radiusKm, 25) / 111.0;
        double lonPad = pad / Math.max(0.2, Math.cos(Math.toRadians(latitude)));
        return repository.findInBox(latitude - pad, longitude - lonPad, latitude + pad, longitude + lonPad,
                        LocalDateTime.now(clock).minusDays(WINDOW_DAYS))
                .stream().limit(500).map(ReportView::of).toList();
    }

    /** Recent reports in the box around all candidate routes. */
    @Transactional(readOnly = true)
    public List<IncidentReport> reportsAround(List<RouteCandidate> candidates) {
        double south = Double.MAX_VALUE;
        double west = Double.MAX_VALUE;
        double north = -Double.MAX_VALUE;
        double east = -Double.MAX_VALUE;
        for (RouteCandidate candidate : candidates) {
            for (GeoPoint p : candidate.geometry()) {
                south = Math.min(south, p.lat());
                north = Math.max(north, p.lat());
                west = Math.min(west, p.lon());
                east = Math.max(east, p.lon());
            }
        }
        if (south == Double.MAX_VALUE) {
            return List.of();
        }
        return repository.findInBox(south - PAD_DEGREES, west - PAD_DEGREES, north + PAD_DEGREES, east + PAD_DEGREES,
                LocalDateTime.now(clock).minusDays(WINDOW_DAYS));
    }
}
