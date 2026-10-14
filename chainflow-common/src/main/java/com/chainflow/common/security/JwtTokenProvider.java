package com.chainflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public final class JwtTokenProvider {
    private final SecretKey key;
    private final long expirationSeconds;

    public JwtTokenProvider(String secret, long expirationSeconds) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(String subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null) {
            throw new IllegalArgumentException("JWT expiration is required");
        }
        return claims;
    }

    /**
     * Returns the normalized role carried by a token's {@code role} claim.
     *
     * <p>Roles are deliberately kept as a single claim for the first version
     * of the platform.  A missing or blank role is treated as absent so the
     * gateway can deny write operations without guessing a default role.</p>
     */
    public String getRole(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object value = claims.get("role");
        if (!(value instanceof String role) || role.isBlank()) {
            return null;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    /** Parse and return the normalized role from a signed token. */
    public String getRole(String token) {
        return getRole(parse(token));
    }
}
