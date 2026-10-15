package com.chainflow.gateway.filter;

import com.chainflow.common.web.TraceIds;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdWebFilterTest {
    private final TraceIdWebFilter filter = new TraceIdWebFilter();

    @Test
    void forwardsCallerTraceAndReturnsItOnAuthorizationRejection() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders/42")
                .header(TraceIds.HEADER, "caller-trace-42"));

        filter.filter(exchange, forwarded -> {
            assertEquals("caller-trace-42", forwarded.getRequest().getHeaders().getFirst(TraceIds.HEADER));
            forwarded.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return forwarded.getResponse().setComplete();
        }).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertEquals("caller-trace-42", exchange.getResponse().getHeaders().getFirst(TraceIds.HEADER));
    }

    @Test
    void generatesTraceAndKeepsExactlyOneHeaderValue() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/health")
                .header(TraceIds.HEADER, "a".repeat(65)));

        filter.filter(exchange, forwarded -> {
            String generated = forwarded.getRequest().getHeaders().getFirst(TraceIds.HEADER);
            assertNotNull(generated);
            assertTrue(generated.matches("[a-f0-9]{32}"));
            forwarded.getResponse().getHeaders().add(TraceIds.HEADER, "downstream-trace");
            return forwarded.getResponse().setComplete();
        }).block();

        assertEquals(1, exchange.getResponse().getHeaders().get(TraceIds.HEADER).size());
        assertTrue(exchange.getResponse().getHeaders().getFirst(TraceIds.HEADER).matches("[a-f0-9]{32}"));
    }
}
