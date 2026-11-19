package com.saferoute.ai.dto;

import java.time.LocalDateTime;

import com.saferoute.ai.model.IncidentReport;
import com.saferoute.ai.model.ReportCategory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record ReportRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull(message = "Choose what you want to report.") ReportCategory category,
            @Size(max = 280, message = "Keep the note under 280 characters.") String note) {
    }

    /** Public view of a report: no information about who made it. */
    public record ReportView(Long id, double latitude, double longitude, ReportCategory category,
                             String categoryLabel, boolean positive, String note, LocalDateTime createdAt) {
        public static ReportView of(IncidentReport r) {
            return new ReportView(r.getId(), r.getLatitude(), r.getLongitude(), r.getCategory(),
                    r.getCategory().getLabel(), r.getCategory().getSeverity() < 0, r.getNote(), r.getCreatedAt());
        }
    }
}
