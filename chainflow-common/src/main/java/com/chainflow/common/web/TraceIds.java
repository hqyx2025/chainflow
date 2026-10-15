package com.chainflow.common.web;

import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Pattern;

/** Shared trace-id header name and validation used at every HTTP boundary. */
public final class TraceIds {
    public static final String HEADER = "X-Trace-Id";
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private TraceIds() {
    }

    /**
     * Keeps a caller supplied id only when it is bounded and header-safe;
     * otherwise creates a new id so logs cannot be polluted with arbitrary input.
     */
    public static String resolve(String candidate) {
        String value = candidate == null ? null : candidate.trim();
        return StringUtils.hasText(value) && SAFE_TRACE_ID.matcher(value).matches()
                ? value : UUID.randomUUID().toString().replace("-", "");
    }
}
