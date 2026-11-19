package com.saferoute.ai.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** The result of sending one alert to one contact over one channel. */
@Entity
@Table(name = "alert_deliveries", indexes = @Index(name = "idx_delivery_alert", columnList = "alert_id"))
public class AlertDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "contact_name", nullable = false, length = 80)
    private String contactName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeliveryStatus status;

    /** Human-readable result: where it went, or why it failed / was skipped. */
    @Column(length = 300)
    private String detail;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public AlertDelivery() {
    }

    public AlertDelivery(Long alertId, Long contactId, String contactName, DeliveryChannel channel,
                         DeliveryStatus status, String detail) {
        this.alertId = alertId;
        this.contactId = contactId;
        this.contactName = contactName;
        this.channel = channel;
        this.status = status;
        this.detail = detail;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getAlertId() { return alertId; }
    public Long getContactId() { return contactId; }
    public String getContactName() { return contactName; }
    public DeliveryChannel getChannel() { return channel; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
