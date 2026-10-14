package com.chainflow.common.security;

import io.jsonwebtoken.Claims;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

/**
 * Stores revoked JWT identifiers in Redis until the token itself expires.
 * A token's JTI is preferred; the token hash is used for backwards
 * compatibility with tokens issued before JTI support was added.
 */
public final class JwtRevocationService implements RevocationService {
    private static final String KEY_PREFIX = "chainflow:auth:revoked:";

    private final RedisOperations<String, String> redis;
    private final JwtTokenProvider tokenProvider;

    public JwtRevocationService(RedisOperations<String, String> redis, JwtTokenProvider tokenProvider) {
        this.redis = redis;
        this.tokenProvider = tokenProvider;
    }

    public void revoke(String token) {
        Claims claims = tokenProvider.parse(token);
        Duration ttl = ttl(claims);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redis.opsForValue().set(key(token, claims), "1", ttl);
        } catch (DataAccessException ex) {
            throw new RevocationStoreUnavailableException(ex);
        }
    }

    public boolean isRevoked(String token) {
        Claims claims = tokenProvider.parse(token);
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(token, claims)));
        } catch (DataAccessException ex) {
            throw new RevocationStoreUnavailableException(ex);
        }
    }

    private Duration ttl(Claims claims) {
        return Duration.between(Instant.now(), claims.getExpiration().toInstant());
    }

    private String key(String token, Claims claims) {
        String identity = claims.getId() == null ? "token:" + token : "jti:" + claims.getId();
        return KEY_PREFIX + sha256(identity);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >>> 4) & 0xf, 16));
                hex.append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
