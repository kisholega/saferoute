package com.saferoute.ai.dto;

import java.time.Instant;
import java.util.List;

import com.saferoute.ai.model.AlertDelivery;
import com.saferoute.ai.model.AlertStatus;
import com.saferoute.ai.model.AlertType;
import com.saferoute.ai.model.DeliveryChannel;
import com.saferoute.ai.model.DeliveryStatus;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public final class AlertDtos {

    private AlertDtos() {
    }

    /** Position report from the phone. Every field is optional: an SOS must work even without GPS. */
    public record LocationReport(
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @DecimalMin("0") Double accuracy,
            @Min(0) @Max(100) Integer battery) {
    }

    public record DeliveryView(String contactName, DeliveryChannel channel, DeliveryStatus status, String detail) {
        public static DeliveryView of(AlertDelivery d) {
            return new DeliveryView(d.getContactName(), d.getChannel(), d.getStatus(), d.getDetail());
        }
    }

    public record AlertView(Long id, AlertType type, AlertStatus status, Long tripId, Double latitude, Double longitude,
                            Double accuracy, Integer battery, String message, Instant createdAt, Instant resolvedAt,
                            String trackUrl, List<DeliveryView> deliveries, boolean deliveryInProgress) {
    }
}
