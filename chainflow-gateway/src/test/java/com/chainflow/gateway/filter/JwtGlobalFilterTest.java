package com.chainflow.gateway.filter;

import com.chainflow.common.security.JwtTokenProvider;
import com.chainflow.common.security.RevocationService;
import com.chainflow.common.security.RevocationStoreUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtGlobalFilterTest {
    private static final String SECRET = "12345678901234567890123456789012";
    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(SECRET, 60);
    private RevocationService revocations;
    private GatewayFilterChain chain;
    private JwtGlobalFilter filter;

    @BeforeEach
    void setUp() {
        revocations = mock(RevocationService.class);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("revocations", revocations);
        filter = new JwtGlobalFilter(tokenProvider, beanFactory.getBeanProvider(RevocationService.class));
    }

    @ParameterizedTest
    @CsvSource({
            "PROCUREMENT, POST, /api/v1/purchases, true",
            "PROCUREMENT, POST, /api/v1/purchases/42/receive, false",
            "WAREHOUSE, POST, /api/v1/purchases, false",
            "WAREHOUSE, POST, /api/v1/purchases/42/receive, true",
            "WAREHOUSE, POST, /api/v1/inventory/receipts, true",
            "SALES, POST, /api/v1/inventory/receipts, false",
            "SALES, POST, /api/v1/orders, true",
            "SALES, POST, /api/v1/orders/42/pay, true",
            "PROCUREMENT, POST, /api/v1/orders/42/pay, false",
            "WAREHOUSE, POST, /api/v1/fulfillments, true",
            "WAREHOUSE, PATCH, /api/v1/fulfillments/42/status, true",
            "SALES, PATCH, /api/v1/fulfillments/42/status, false",
            "READ_ONLY, GET, /api/v1/orders/42, true",
            "READ_ONLY, POST, /api/v1/orders, false",
            "READ_ONLY, PUT, /api/v1/inventory/42, false",
            "READ_ONLY, DELETE, /api/v1/purchases/42, false",
            "ADMIN, POST, /api/v1/purchases, true",
            "ADMIN, POST, /api/v1/inventory/receipts, true",
            "ADMIN, POST, /api/v1/orders, true",
            "ADMIN, PATCH, /api/v1/fulfillments/42/status, true",
            "SUPPLY_CHAIN_OPERATOR, POST, /api/v1/purchases, true",
            "SUPPLY_CHAIN_OPERATOR, POST, /api/v1/purchases/42/receive, true",
            "SUPPLY_CHAIN_OPERATOR, POST, /api/v1/inventory/receipts, true",
            "SUPPLY_CHAIN_OPERATOR, POST, /api/v1/orders/42/pay, true",
            "SUPPLY_CHAIN_OPERATOR, PATCH, /api/v1/fulfillments/42/status, true"
    })
    void appliesRoleBoundaries(String role, String method, String path, boolean allowed) {
        var exchange = authorized(HttpMethod.valueOf(method), path, tokenProvider.createToken("operator", role));

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        if (allowed) {
            assertNull(exchange.getResponse().getStatusCode());
            verify(chain).filter(exchange);
        } else {
            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
            verifyNoInteractions(chain);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/auth/login",
            "GET, /api/v1/auth/health",
            "GET, /actuator/health",
            "GET, /actuator/health/readiness"
    })
    void permitsOnlyAnonymousLoginAndHealth(String method, String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path));

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        verify(chain).filter(exchange);
        verifyNoInteractions(revocations);
    }

    @ParameterizedTest
    @CsvSource({"GET, /api/v1/orders/42", "GET, /actuator/metrics", "GET, /api/v1/auth/login", "POST, /api/v1/auth/logout"})
    void requiresAuthenticationForProtectedEndpoints(String method, String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path));

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain, revocations);
    }

    @Test
    void deniesBusinessWritesWhenRoleIsMissing() {
        var exchange = authorized(HttpMethod.POST, "/api/v1/orders", tokenProvider.createToken("operator", null));

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    void ignoresSpoofedRoleHeader() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.createToken("operator", "READ_ONLY"))
                .header("X-Role", "ADMIN"));

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/purchases;foo=bar",
            "/api/v1/purchases/42/receive;foo=bar",
            "/api/v1/%6frders",
            "/api/v1/orders;foo=bar/42/pay"
    })
    void encodedSegmentsAndMatrixParametersCannotBypassWriteAuthorization(String path) {
        var exchange = authorized(HttpMethod.POST, path, tokenProvider.createToken("operator", "READ_ONLY"));

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsRevokedTokensBeforeContinuingChain() {
        String token = tokenProvider.createToken("operator", "ADMIN");
        when(revocations.isRevoked(token)).thenReturn(true);
        var exchange = authorized(HttpMethod.POST, "/api/v1/orders", token);

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        when(revocations.isRevoked(anyString())).thenThrow(new RevocationStoreUnavailableException(new RuntimeException("redis down")));
        var exchange = authorized(HttpMethod.GET, "/api/v1/orders/42", tokenProvider.createToken("operator", "ADMIN"));

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    void failsClosedWithoutRevocationService() {
        var exchange = authorized(HttpMethod.POST, "/api/v1/orders", tokenProvider.createToken("operator", "ADMIN"));

        new JwtGlobalFilter(tokenProvider).filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsExpiredTokensBeforeCallingRedis() {
        String token = new JwtTokenProvider(SECRET, -1).createToken("operator", "ADMIN");
        var exchange = authorized(HttpMethod.GET, "/api/v1/orders/42", token);

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verifyNoInteractions(chain, revocations);
    }

    @Test
    void movesBlockingRevocationLookupOffTheCallingThread() {
        AtomicReference<String> lookupThread = new AtomicReference<>();
        when(revocations.isRevoked(anyString())).thenAnswer(invocation -> {
            lookupThread.set(Thread.currentThread().getName());
            return false;
        });
        var exchange = authorized(HttpMethod.GET, "/api/v1/orders/42", tokenProvider.createToken("operator", "ADMIN"));

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertTrue(lookupThread.get().startsWith("boundedElastic-"), lookupThread.get());
        verify(chain).filter(exchange);
    }

    private MockServerWebExchange authorized(HttpMethod method, String path, String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
