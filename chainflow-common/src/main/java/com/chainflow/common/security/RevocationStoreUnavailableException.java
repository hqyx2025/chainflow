package com.chainflow.common.security;

/**
 * Indicates that the revocation store could not be reached. Callers should
 * fail closed and report a temporary authentication dependency failure.
 */
public class RevocationStoreUnavailableException extends RuntimeException {
    public RevocationStoreUnavailableException(Throwable cause) {
        super("JWT revocation store is unavailable", cause);
    }
}
