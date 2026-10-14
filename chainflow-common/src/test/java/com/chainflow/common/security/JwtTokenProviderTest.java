package com.chainflow.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {
    private final JwtTokenProvider provider = new JwtTokenProvider("12345678901234567890123456789012", 60);

    @Test
    void createsAndParsesToken() {
        String token = provider.createToken("operator", "SUPPLY_CHAIN_OPERATOR");
        var claims = provider.parse(token);
        assertEquals("operator", claims.getSubject());
        assertEquals("SUPPLY_CHAIN_OPERATOR", claims.get("role", String.class));
        assertEquals("SUPPLY_CHAIN_OPERATOR", provider.getRole(claims));
    }

    @Test
    void normalizesRoleClaimAndDoesNotInventAMissingRole() {
        assertEquals("WAREHOUSE", provider.getRole(provider.createToken("operator", " warehouse ")));
        assertNull(provider.getRole(provider.createToken("operator", null)));
        assertNull(provider.getRole(provider.createToken("operator", " ")));
        assertNull(provider.getRole(io.jsonwebtoken.Jwts.claims().add("role", 42).build()));
    }

    @Test
    void rejectsTooShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("short", 60));
    }

    @Test
    void revokesTokenUntilItsExpiration() {
        RedisOperations<String, String> redis = mock(RedisOperations.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        JwtRevocationService revocation = new JwtRevocationService(redis, provider);
        String token = provider.createToken("operator", "OPERATOR");

        revocation.revoke(token);

        verify(values).set(anyString(), eq("1"), any(java.time.Duration.class));
        when(redis.hasKey(anyString())).thenReturn(true);
        assertTrue(revocation.isRevoked(token));
    }

    @Test
    void reportsRedisOutageClearly() {
        RedisOperations<String, String> redis = mock(RedisOperations.class);
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));
        JwtRevocationService revocation = new JwtRevocationService(redis, provider);
        String token = provider.createToken("operator", "OPERATOR");

        assertThrows(RevocationStoreUnavailableException.class, () -> revocation.isRevoked(token));
    }
}
