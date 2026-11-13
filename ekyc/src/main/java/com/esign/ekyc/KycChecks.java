package com.esign.ekyc;

import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;
import com.esign.common.validation.SignerIds;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Set;

public final class KycChecks {
    private static final Set<String> PHOTO_FORMATS = Set.of("BMP", "JPG", "PNG");

    private KycChecks() {
    }

    public static void require(boolean condition, EsignErrorCode code) {
        if (!condition) {
            throw new ApiException(code);
        }
    }

    public static boolean email(String email) {
        return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    public static boolean dob(String value) {
        if (value == null || !value.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return false;
        }
        try {
            LocalDate date = LocalDate.parse(value);
            return !date.isAfter(LocalDate.now());
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    public static boolean country(String value) {
        return value != null && value.matches("^[A-Z]{2}$");
    }

    public static boolean postal(String country, String postal) {
        if (postal == null || postal.isBlank() || postal.length() > 12) {
            return false;
        }
        if ("IN".equals(country)) {
            return postal.matches("^[0-9]{6}$");
        }
        return postal.matches("^[A-Za-z0-9 -]{3,12}$");
    }

    public static boolean gender(String value) {
        return "M".equals(value) || "F".equals(value) || "O".equals(value);
    }

    public static boolean photoFormat(String format) {
        return PHOTO_FORMATS.contains(format);
    }

    public static boolean photo(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value.replaceAll("\\s", ""));
            return decoded.length > 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean aadhaar(String value) {
        return value != null && value.matches("^[0-9]{4}$");
    }

    public static boolean pan(String value) {
        return SignerIds.validPan(value);
    }

    public static boolean mobile(String value) {
        return SignerIds.validMobile(value);
    }
}
