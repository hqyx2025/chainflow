package com.chainflow.gateway.config;

import com.chainflow.common.security.JwtTokenProvider;
import com.chainflow.common.security.JwtRevocationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class GatewayJwtConfig {
    @Bean
    JwtTokenProvider gatewayJwtTokenProvider(@Value("${chainflow.security.jwt-secret}") String secret,
                                             @Value("${chainflow.security.expiration-seconds:7200}") long expirationSeconds) {
        return new JwtTokenProvider(secret, expirationSeconds);
    }

    @Bean
    JwtRevocationService jwtRevocationService(StringRedisTemplate redis, JwtTokenProvider tokenProvider) {
        return new JwtRevocationService(redis, tokenProvider);
    }
}
