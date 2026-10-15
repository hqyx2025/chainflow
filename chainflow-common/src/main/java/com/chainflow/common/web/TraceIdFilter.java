package com.chainflow.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String TRACE_ID_HEADER = TraceIds.HEADER;
    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = TraceIds.resolve(request.getHeader(TRACE_ID_HEADER));
        response.setHeader(TRACE_ID_HEADER, traceId);
        String previousTraceId = MDC.get(TRACE_ID_HEADER);
        long started = System.nanoTime();
        boolean completed = false;
        try {
            MDC.put(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            if (!request.getRequestURI().startsWith("/actuator/")) {
                log.info("HTTP {} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        completed ? response.getStatus() : 500, (System.nanoTime() - started) / 1_000_000);
            }
            if (previousTraceId == null) {
                MDC.remove(TRACE_ID_HEADER);
            } else {
                MDC.put(TRACE_ID_HEADER, previousTraceId);
            }
        }
    }
}
