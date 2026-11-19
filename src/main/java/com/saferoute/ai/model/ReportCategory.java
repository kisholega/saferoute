package com.saferoute.ai.model;

/** What a community member reported about a spot. SAFE_SPOT is positive feedback. */
public enum ReportCategory {
    POOR_LIGHTING("Poor lighting", 2),
    HARASSMENT("Harassment", 4),
    ISOLATED("Isolated / deserted", 2),
    UNSAFE_CROWD("Unsafe crowd", 3),
    OTHER("Other concern", 1),
    SAFE_SPOT("Felt safe here", -2);

    private final String label;
    private final int severity;

    ReportCategory(String label, int severity) {
        this.label = label;
        this.severity = severity;
    }

    public String getLabel() {
        return label;
    }

    /** Contribution to the incident feature; negative values make an area look safer. */
    public int getSeverity() {
        return severity;
    }
}
