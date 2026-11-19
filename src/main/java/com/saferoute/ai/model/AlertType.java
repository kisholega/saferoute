package com.saferoute.ai.model;

public enum AlertType {
    SOS,
    CHECK_IN_MISSED,
    LOST_CONTACT,
    TRIP_STARTED,
    ARRIVED,
    ALL_CLEAR,
    TEST;

    /** Emergencies stay ACTIVE until resolved; the others are one-off notifications. */
    public boolean isEmergency() {
        return this == SOS || this == CHECK_IN_MISSED || this == LOST_CONTACT;
    }
}
