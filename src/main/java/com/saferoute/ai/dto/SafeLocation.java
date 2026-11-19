package com.saferoute.ai.dto;

/**
 * A place where someone can find help.
 *
 * @param type POLICE, HOSPITAL, CLINIC, PHARMACY or FUEL
 */
public record SafeLocation(
        String name,
        String type,
        double latitude,
        double longitude,
        double distanceKm,
        boolean open24h) {
}
