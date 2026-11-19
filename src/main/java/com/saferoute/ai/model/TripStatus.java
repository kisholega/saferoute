package com.saferoute.ai.model;

public enum TripStatus {
    /** Travelling normally. */
    ACTIVE,
    /** Something looked wrong (off route, overdue); waiting for the user to confirm they are OK. */
    CHECK_IN_REQUIRED,
    /** Contacts were alerted (missed check-in, lost contact, or the user asked for help). */
    ALERTED,
    /** Reached the destination. */
    ARRIVED,
    /** Ended by the user before arriving. */
    ENDED;

    public boolean isLive() {
        return this == ACTIVE || this == CHECK_IN_REQUIRED || this == ALERTED;
    }
}
