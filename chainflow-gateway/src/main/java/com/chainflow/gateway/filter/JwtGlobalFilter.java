package com.chainflow.gateway.filter;

import com.chainflow.common.security.JwtTokenProvider;
import com.chainflow.common.security.RevocationService;
import com.chainflow.common.security.RevocationStoreUnavailableException;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtGlobalFilter implements GlobalFilter, Ordered {
    private final JwtTokenProvider tokenProvider;
    private final RevocationService revocationService;
    public JwtGlobalFilter(JwtTokenProvider tokenProvider, ObjectProvider<RevocationService> revocationServiceProvider) {
        this.tokenProvider = tokenProvider;
        this.revocationService = revocationServiceProvider.getIfAvailable();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/health")
                || path.startsWith("/actuator/")) return chain.filter(exchange);
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                || header.length() <= 7) return unauthorized(exchange);
        try {
            String token = header.substring(7).trim();
            tokenProvider.parse(token);
            if (revocationService == null) return unavailable(exchange);
            if (revocationService.isRevoked(token)) return unauthorized(exchange);
            return chain.filter(exchange);
        } catch (RevocationStoreUnavailableException ex) {
            return unavailable(exchange);
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unavailable(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() { return -100; }
}
