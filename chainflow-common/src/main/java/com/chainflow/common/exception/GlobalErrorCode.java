package com.chainflow.common.exception;

public final class GlobalErrorCode {
    public static final String INVALID_ARGUMENT = "400";
    public static final String UNAUTHORIZED = "401";
    public static final String NOT_FOUND = "404";
    public static final String CONFLICT = "409";
    public static final String INTERNAL_ERROR = "500";

    private GlobalErrorCode() {
    }
}
