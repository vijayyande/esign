package com.esign.common.validation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signer id format from section 3.3.1.1: {@code id@id-type.esp-id}.
 * Allowed id types are {@code username}, {@code Mobile}, and {@code PAN}.
 */
public final class SignerIds {
    private static final Pattern PATTERN = Pattern.compile("^([^@\\s]+)@(username|Mobile|PAN)\\.([A-Za-z0-9_-]{1,32})$");
    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern MOBILE = Pattern.compile("^[0-9]{10}$");

    private SignerIds() {
    }

    public static ParsedSignerId parse(String signerId) {
        if (signerId == null) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(signerId.trim());
        if (!matcher.matches()) {
            return null;
        }
        String id = matcher.group(1);
        String type = matcher.group(2);
        if ("Mobile".equals(type) && !MOBILE.matcher(id).matches()) {
            return null;
        }
        if ("PAN".equals(type) && !PAN.matcher(id).matches()) {
            return null;
        }
        return new ParsedSignerId(id, type, matcher.group(3));
    }

    public static boolean validPan(String pan) {
        return pan != null && PAN.matcher(pan).matches();
    }

    public static boolean validMobile(String mobile) {
        return mobile != null && MOBILE.matcher(mobile).matches();
    }
}
