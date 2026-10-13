package com.chainflow.gateway.config;

import com.chainflow.common.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayJwtConfig {
    @Bean
    JwtTokenProvider gatewayJwtTokenProvider(@Value("${chainflow.security.jwt-secret}") String secret,
                                             @Value("${chainflow.security.expiration-seconds:7200}") long expirationSeconds) {
        return new JwtTokenProvider(secret, expirationSeconds);
    }
}
