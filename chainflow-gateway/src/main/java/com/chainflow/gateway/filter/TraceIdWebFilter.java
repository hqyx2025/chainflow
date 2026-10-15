package com.chainflow.gateway.filter;

import com.chainflow.common.web.TraceIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/** Creates a bounded trace id at the public edge, including rejected requests. */
@Component
public class TraceIdWebFilter implements WebFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(TraceIdWebFilter.class);
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long started = System.nanoTime();
        String traceId = TraceIds.resolve(exchange.getRequest().getHeaders().getFirst(TraceIds.HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(TraceIds.HEADER, traceId)
                .build();
        exchange.getResponse().getHeaders().set(TraceIds.HEADER, traceId);
        // A service response must not introduce a second value for the same trace.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(TraceIds.HEADER, traceId);
            return Mono.empty();
        });
        return chain.filter(exchange.mutate().request(request).build()).doFinally(signal -> {
            if (!request.getPath().value().startsWith("/actuator/")) {
                var status = exchange.getResponse().getStatusCode();
                // Reactor work can hop threads, so use an explicit traceId field instead of thread-local MDC.
                log.info("HTTP {} {} -> {} ({} ms) traceId={}", request.getMethod(), request.getPath().value(),
                        status == null ? (signal == SignalType.ON_COMPLETE ? 200 : 500) : status.value(),
                        (System.nanoTime() - started) / 1_000_000, traceId);
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
