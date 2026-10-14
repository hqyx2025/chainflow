package com.chainflow.auth.config;

import com.chainflow.common.security.JwtRevocationService;
import com.chainflow.common.security.JwtTokenProvider;
import com.chainflow.common.security.RevocationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class AuthRevocationConfig {
    @Bean
    RevocationService jwtRevocationService(StringRedisTemplate redis, JwtTokenProvider tokenProvider) {
        return new JwtRevocationService(redis, tokenProvider);
    }
}
