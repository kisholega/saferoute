package com.saferoute.ai.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** A Safe Trip: a chosen route being travelled, with live position and safety state. */
@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TravelMode mode;

    @Column(name = "origin_name", nullable = false)
    private String originName;

    @Column(name = "destination_name", nullable = false)
    private String destinationName;

    @Column(name = "destination_latitude", nullable = false)
    private Double destinationLatitude;

    @Column(name = "destination_longitude", nullable = false)
    private Double destinationLongitude;

    /** The planned route as JSON [[lat,lon],...]. */
    @Lob
    @Column(name = "route_geometry", nullable = false)
    private String routeGeometry;

    /** Per-stretch risk as JSON (same shape as the routes API), used by the tracking page. */
    @Lob
    @Column(name = "route_segments")
    private String routeSegments;

    @Column(name = "distance_km", nullable = false)
    private Double distanceKm;

    @Column(name = "expected_minutes", nullable = false)
    private Integer expectedMinutes;

    @Column(name = "safety_score")
    private Double safetyScore;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant eta;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "share_token", nullable = false, unique = true, length = 40)
    private String shareToken;

    @Column(name = "last_latitude")
    private Double lastLatitude;

    @Column(name = "last_longitude")
    private Double lastLongitude;

    @Column(name = "last_accuracy")
    private Double lastAccuracy;

    @Column(name = "last_battery")
    private Integer lastBattery;

    @Column(name = "last_ping_at")
    private Instant lastPingAt;

    /** 0..1 along the route. */
    @Column(nullable = false)
    private Double progress = 0.0;

    @Column(name = "remaining_km")
    private Double remainingKm;

    @Column(name = "deviation_meters")
    private Double deviationMeters;

    @Column(name = "off_route_since")
    private Instant offRouteSince;

    @Column(name = "check_in_reason")
    private String checkInReason;

    @Column(name = "check_in_deadline")
    private Instant checkInDeadline;

    @Column(name = "overdue_check_done", nullable = false)
    private Boolean overdueCheckDone = false;

    @Column(name = "notify_contacts", nullable = false)
    private Boolean notifyContacts = true;

    @Column(nullable = false)
    private Boolean simulated = false;

    @Column(name = "alert_id")
    private Long alertId;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public TripStatus getStatus() { return status; }
    public void setStatus(TripStatus status) { this.status = status; }
    public TravelMode getMode() { return mode; }
    public void setMode(TravelMode mode) { this.mode = mode; }
    public String getOriginName() { return originName; }
    public void setOriginName(String originName) { this.originName = originName; }
    public String getDestinationName() { return destinationName; }
    public void setDestinationName(String destinationName) { this.destinationName = destinationName; }
    public Double getDestinationLatitude() { return destinationLatitude; }
    public void setDestinationLatitude(Double v) { this.destinationLatitude = v; }
    public Double getDestinationLongitude() { return destinationLongitude; }
    public void setDestinationLongitude(Double v) { this.destinationLongitude = v; }
    public String getRouteGeometry() { return routeGeometry; }
    public void setRouteGeometry(String routeGeometry) { this.routeGeometry = routeGeometry; }
    public String getRouteSegments() { return routeSegments; }
    public void setRouteSegments(String routeSegments) { this.routeSegments = routeSegments; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public Integer getExpectedMinutes() { return expectedMinutes; }
    public void setExpectedMinutes(Integer expectedMinutes) { this.expectedMinutes = expectedMinutes; }
    public Double getSafetyScore() { return safetyScore; }
    public void setSafetyScore(Double safetyScore) { this.safetyScore = safetyScore; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEta() { return eta; }
    public void setEta(Instant eta) { this.eta = eta; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }
    public Double getLastLatitude() { return lastLatitude; }
    public void setLastLatitude(Double v) { this.lastLatitude = v; }
    public Double getLastLongitude() { return lastLongitude; }
    public void setLastLongitude(Double v) { this.lastLongitude = v; }
    public Double getLastAccuracy() { return lastAccuracy; }
    public void setLastAccuracy(Double v) { this.lastAccuracy = v; }
    public Integer getLastBattery() { return lastBattery; }
    public void setLastBattery(Integer v) { this.lastBattery = v; }
    public Instant getLastPingAt() { return lastPingAt; }
    public void setLastPingAt(Instant v) { this.lastPingAt = v; }
    public Double getProgress() { return progress; }
    public void setProgress(Double progress) { this.progress = progress; }
    public Double getRemainingKm() { return remainingKm; }
    public void setRemainingKm(Double remainingKm) { this.remainingKm = remainingKm; }
    public Double getDeviationMeters() { return deviationMeters; }
    public void setDeviationMeters(Double v) { this.deviationMeters = v; }
    public Instant getOffRouteSince() { return offRouteSince; }
    public void setOffRouteSince(Instant v) { this.offRouteSince = v; }
    public String getCheckInReason() { return checkInReason; }
    public void setCheckInReason(String v) { this.checkInReason = v; }
    public Instant getCheckInDeadline() { return checkInDeadline; }
    public void setCheckInDeadline(Instant v) { this.checkInDeadline = v; }
    public Boolean getOverdueCheckDone() { return overdueCheckDone; }
    public void setOverdueCheckDone(Boolean v) { this.overdueCheckDone = v; }
    public Boolean getNotifyContacts() { return notifyContacts; }
    public void setNotifyContacts(Boolean v) { this.notifyContacts = v; }
    public Boolean getSimulated() { return simulated; }
    public void setSimulated(Boolean simulated) { this.simulated = simulated; }
    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }
}
