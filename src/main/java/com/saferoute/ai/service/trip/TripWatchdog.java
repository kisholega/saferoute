package com.saferoute.ai.service.trip;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 5 seconds: checks live trips for missed check-ins, leaving the route, overdue arrival and silent phones. */
@Component
public class TripWatchdog {

    private final TripService tripService;
    private final Clock clock;

    public TripWatchdog(TripService tripService, Clock clock) {
        this.tripService = tripService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
    public void tick() {
        tripService.runChecks(clock.instant());
    }
}
