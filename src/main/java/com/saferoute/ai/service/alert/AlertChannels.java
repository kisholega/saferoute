package com.saferoute.ai.service.alert;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.saferoute.ai.client.NtfyClient;
import com.saferoute.ai.client.NtfyClient.Action;
import com.saferoute.ai.client.TwilioClient;
import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.model.DeliveryChannel;
import com.saferoute.ai.model.EmergencyContact;

/**
 * The three ways SafeRoute reaches a contact from the server.
 * <ul>
 *   <li><b>Push</b> (ntfy): on by default, free, no keys. The contact subscribes to their private topic.</li>
 *   <li><b>Email</b>: on when SMTP is configured (spring.mail.*) and the contact has an email address.</li>
 *   <li><b>SMS</b>: on when Twilio credentials are configured.</li>
 * </ul>
 */
@Component
public class AlertChannels {

    private final NtfyClient ntfy;
    private final TwilioClient twilio;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final SafeRouteProperties.Alerts config;
    private final String mailUsername;

    public AlertChannels(NtfyClient ntfy, TwilioClient twilio, ObjectProvider<JavaMailSender> mailSender,
                         SafeRouteProperties properties,
                         org.springframework.core.env.Environment environment) {
        this.ntfy = ntfy;
        this.twilio = twilio;
        this.mailSender = mailSender;
        this.config = properties.alerts();
        this.mailUsername = environment.getProperty("spring.mail.username", "");
    }

    public boolean isConfigured(DeliveryChannel channel) {
        return switch (channel) {
            case PUSH -> config.pushEnabled();
            case EMAIL -> mailSender.getIfAvailable() != null && !mailUsername.isBlank();
            case SMS -> twilio.isConfigured();
        };
    }

    /** null when the channel can be used for this contact, otherwise the reason it is skipped. */
    public String skipReason(DeliveryChannel channel, EmergencyContact contact) {
        if (!isConfigured(channel)) {
            return switch (channel) {
                case PUSH -> "Push notifications are switched off";
                case EMAIL -> "Email sending is not set up on the server";
                case SMS -> "SMS sending is not set up on the server";
            };
        }
        if (channel == DeliveryChannel.EMAIL && (contact.getEmail() == null || contact.getEmail().isBlank())) {
            return "No email address saved for this contact";
        }
        if (channel == DeliveryChannel.PUSH && contact.getPushTopic() == null) {
            return "No push topic for this contact";
        }
        return null;
    }

    /** Sends and returns a short description of where it went. Throws on failure. */
    public String send(DeliveryChannel channel, EmergencyContact contact, AlertMessage message) {
        return switch (channel) {
            case PUSH -> sendPush(contact, message);
            case EMAIL -> sendEmail(contact, message);
            case SMS -> "SMS " + twilio.send(contact.getPhone(), message.sms());
        };
    }

    private String sendPush(EmergencyContact contact, AlertMessage message) {
        List<Action> actions = new ArrayList<>();
        if (message.trackUrl() != null) {
            actions.add(new Action("Live location", message.trackUrl()));
        }
        if (message.mapUrl() != null) {
            actions.add(new Action("Open map", message.mapUrl()));
        }
        if (message.callUrl() != null && actions.size() < 3) {
            actions.add(new Action("Call", message.callUrl()));
        }
        String click = message.mapUrl() != null ? message.mapUrl() : message.trackUrl();
        String id = ntfy.publish(contact.getPushTopic(), message.title(), message.body(), message.priority(),
                message.tags(), click, actions);
        return "Push notification " + id;
    }

    private String sendEmail(EmergencyContact contact, AlertMessage message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("Email sending is not set up on the server");
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        String from = config.mailFrom().isBlank() ? mailUsername : config.mailFrom();
        mail.setFrom("SafeRoute AI <" + from + ">");
        mail.setTo(contact.getEmail());
        mail.setSubject(message.title());
        mail.setText("Hi " + contact.getName() + ",\n\n" + message.body()
                + "\n\n— SafeRoute AI\nYou receive this because you are an emergency contact in SafeRoute AI.");
        sender.send(mail);
        return "Email to " + contact.getEmail();
    }

    public String pushBaseUrl() {
        return ntfy.baseUrl();
    }
}
