package com.chainflow.common.security;

/** Shared contract for checking and recording revoked access tokens. */
public interface RevocationService {
    void revoke(String token);

    boolean isRevoked(String token);
}
