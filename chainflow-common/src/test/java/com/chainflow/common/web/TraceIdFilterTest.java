package com.chainflow.common.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class TraceIdFilterTest {
    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void acceptsSafeCallerIdAndClearsMdcAfterRequest() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader(TraceIds.HEADER, "request_42.v1");

        filter.doFilter(request, response, (req, res) ->
                assertEquals("request_42.v1", MDC.get(TraceIds.HEADER)));

        assertEquals("request_42.v1", response.getHeader(TraceIds.HEADER));
        assertNull(MDC.get(TraceIds.HEADER));
    }

    @Test
    void replacesUnsafeAndOverlongCallerIds() {
        assertTrue(TraceIds.resolve("bad\r\nforged").matches("[a-f0-9]{32}"));
        assertTrue(TraceIds.resolve("a".repeat(65)).matches("[a-f0-9]{32}"));
        assertTrue(TraceIds.resolve(null).matches("[a-f0-9]{32}"));
        assertEquals("a".repeat(64), TraceIds.resolve("a".repeat(64)));
    }

    @Test
    void restoresMdcEvenWhenDownstreamThrows() {
        MDC.put(TraceIds.HEADER, "previous-request");
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                (req, res) -> { throw new ServletException("failed"); }));

        assertEquals("previous-request", MDC.get(TraceIds.HEADER));
        assertTrue(response.getHeader(TraceIds.HEADER).matches("[a-f0-9]{32}"));
    }
}
