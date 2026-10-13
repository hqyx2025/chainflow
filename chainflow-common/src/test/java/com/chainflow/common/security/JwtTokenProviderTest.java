package com.chainflow.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {
    private final JwtTokenProvider provider = new JwtTokenProvider("12345678901234567890123456789012", 60);

    @Test
    void createsAndParsesToken() {
        String token = provider.createToken("operator", "SUPPLY_CHAIN_OPERATOR");
        var claims = provider.parse(token);
        assertEquals("operator", claims.getSubject());
        assertEquals("SUPPLY_CHAIN_OPERATOR", claims.get("role", String.class));
    }

    @Test
    void rejectsTooShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("short", 60));
    }
}
