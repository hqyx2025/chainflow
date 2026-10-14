package com.chainflow.gateway.filter;

import com.chainflow.common.security.JwtTokenProvider;
import com.chainflow.common.security.RevocationService;
import com.chainflow.common.security.RevocationStoreUnavailableException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.stream.Collectors;

@Component
public class JwtGlobalFilter implements GlobalFilter, Ordered {
    private final JwtTokenProvider tokenProvider;
    private final RevocationService revocationService;
    public JwtGlobalFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
        this.revocationService = null;
    }

    @Autowired
    public JwtGlobalFilter(JwtTokenProvider tokenProvider, ObjectProvider<RevocationService> revocationServiceProvider) {
        this.tokenProvider = tokenProvider;
        this.revocationService = revocationServiceProvider.getIfAvailable();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = authorizationPath(exchange);
        HttpMethod method = exchange.getRequest().getMethod();
        if (isAnonymousEndpoint(method, path)) return chain.filter(exchange);
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                || header.length() <= 7) return unauthorized(exchange);
        String token = header.substring(7).trim();
        return Mono.fromCallable(() -> checkAccess(token, method, path))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(decision -> switch (decision) {
                    case ALLOWED -> chain.filter(exchange);
                    case UNAUTHORIZED -> unauthorized(exchange);
                    case FORBIDDEN -> forbidden(exchange);
                    case UNAVAILABLE -> unavailable(exchange);
                });
    }

    private AccessDecision checkAccess(String token, HttpMethod method, String path) {
        try {
            Claims claims = tokenProvider.parse(token);
            if (revocationService == null) return AccessDecision.UNAVAILABLE;
            if (revocationService.isRevoked(token)) return AccessDecision.UNAUTHORIZED;
            if (!BusinessWriteAuthorization.isAllowed(method, path, tokenProvider.getRole(claims))) {
                return AccessDecision.FORBIDDEN;
            }
            return AccessDecision.ALLOWED;
        } catch (RevocationStoreUnavailableException ex) {
            return AccessDecision.UNAVAILABLE;
        } catch (JwtException | IllegalArgumentException ex) {
            return AccessDecision.UNAUTHORIZED;
        }
    }

    private String authorizationPath(ServerWebExchange exchange) {
        // Match the decoded path segments used by Gateway's Path predicates.
        // Matrix parameters must not turn a protected path into an unknown one.
        String path = exchange.getRequest().getPath().pathWithinApplication().elements().stream()
                .map(element -> element instanceof PathContainer.PathSegment segment
                        ? segment.valueToMatch() : element.value())
                .collect(Collectors.joining());
        return StringUtils.cleanPath(path);
    }

    private boolean isAnonymousEndpoint(HttpMethod method, String path) {
        return (HttpMethod.POST.equals(method) && path.equals("/api/v1/auth/login"))
                || (HttpMethod.GET.equals(method) && (path.equals("/api/v1/auth/health")
                || path.equals("/actuator/health") || path.startsWith("/actuator/health/")));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
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

    private enum AccessDecision { ALLOWED, UNAUTHORIZED, FORBIDDEN, UNAVAILABLE }
}
