package com.esign.common.time;

import com.esign.common.error.ApiException;
import com.esign.common.error.EsignErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class IstTimestamps {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final Duration MAX_SKEW = Duration.ofMinutes(30);

    private IstTimestamps() {
    }

    public static String now() {
        return OffsetDateTime.now(IST).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static Instant parse(String value, EsignErrorCode invalid) {
        if (value == null || value.isBlank()) {
            throw new ApiException(invalid);
        }
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (RuntimeException exception) {
            throw new ApiException(invalid);
        }
    }

    public static void requireFresh(String value, Instant now, EsignErrorCode invalid) {
        Instant timestamp = parse(value, invalid);
        Duration skew = Duration.between(timestamp, now).abs();
        if (skew.compareTo(MAX_SKEW) > 0) {
            throw new ApiException(invalid);
        }
    }
}
