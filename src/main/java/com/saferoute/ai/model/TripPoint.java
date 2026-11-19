package com.saferoute.ai.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** One recorded position during a trip or an SOS, used to draw the trail on the tracking page. */
@Entity
@Table(name = "trip_points", indexes = {
        @Index(name = "idx_point_trip", columnList = "trip_id"),
        @Index(name = "idx_point_alert", columnList = "alert_id")})
public class TripPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id")
    private Long tripId;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public TripPoint() {
    }

    public TripPoint(Long tripId, Long alertId, double latitude, double longitude, Instant recordedAt) {
        this.tripId = tripId;
        this.alertId = alertId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Long getAlertId() { return alertId; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Instant getRecordedAt() { return recordedAt; }
}
