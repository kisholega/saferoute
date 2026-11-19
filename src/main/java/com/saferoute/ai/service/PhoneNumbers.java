package com.saferoute.ai.service;

import org.springframework.stereotype.Component;

import com.saferoute.ai.config.SafeRouteProperties;
import com.saferoute.ai.exception.FieldValidationException;

/** Converts what people type into international E.164 numbers, which SMS providers require. */
@Component
public class PhoneNumbers {

    private final String countryCode;

    public PhoneNumbers(SafeRouteProperties properties) {
        this.countryCode = properties.contacts().defaultCountryCode().replaceAll("\\D", "");
    }

    /**
     * "98765 43210" -> "+919876543210", "+44 7700 900123" -> "+447700900123", "09876543210" -> "+919876543210".
     *
     * @param field the form field to attach the error to
     */
    public String normalize(String raw, String field) {
        String input = raw == null ? "" : raw.trim();
        String digits = input.replaceAll("\\D", "");

        String e164;
        if (input.startsWith("+")) {
            e164 = "+" + digits;
        } else if (digits.startsWith("00")) {
            e164 = "+" + digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            e164 = "+" + countryCode + digits.substring(1);
        } else if (digits.length() == 10) {
            e164 = "+" + countryCode + digits;
        } else {
            e164 = "+" + digits;
        }

        if (!e164.matches("\\+[1-9]\\d{7,14}")) {
            throw new FieldValidationException(field, "Enter a valid mobile number, e.g. 98765 43210 or +91 98765 43210.");
        }
        return e164;
    }
}
