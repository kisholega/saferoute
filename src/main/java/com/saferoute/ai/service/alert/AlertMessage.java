package com.saferoute.ai.service.alert;

import java.util.List;

import com.saferoute.ai.model.AlertType;

/**
 * One alert, worded for every channel.
 *
 * @param title    headline, e.g. "🚨 SOS from Priya"
 * @param body     full text for push and email
 * @param sms      short text for SMS (fits in two SMS segments)
 * @param mapUrl   Google Maps link to the position (works on any phone), may be null
 * @param trackUrl live tracking page, may be null
 * @param callUrl  tel: link to call the user, may be null
 * @param priority push priority 1..5
 * @param tags     push emoji tags
 */
public record AlertMessage(AlertType type, String title, String body, String sms, String mapUrl, String trackUrl,
                           String callUrl, int priority, List<String> tags) {
}
