package com.chainflow.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TraceIdRestClientCustomizerTest {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesCurrentTraceWithoutOverwritingExplicitHeader() {
        RestClient.Builder builder = RestClient.builder();
        new TraceIdRestClientCustomizer().customize(builder);
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = builder.build();
        MDC.put(TraceIds.HEADER, "incoming-trace-7");

        server.expect(requestTo("http://inventory/stock"))
                .andExpect(header(TraceIds.HEADER, "incoming-trace-7")).andRespond(withSuccess());
        server.expect(requestTo("http://inventory/explicit"))
                .andExpect(header(TraceIds.HEADER, "explicit-trace")).andRespond(withSuccess());

        client.get().uri("http://inventory/stock").retrieve().toBodilessEntity();
        client.get().uri("http://inventory/explicit").header(TraceIds.HEADER, "explicit-trace")
                .retrieve().toBodilessEntity();
        server.verify();
    }

    @Test
    void doesNotSendStaleTraceOutsideRequest() {
        RestClient.Builder builder = RestClient.builder();
        new TraceIdRestClientCustomizer().customize(builder);
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://inventory/stock"))
                .andExpect(headerDoesNotExist(TraceIds.HEADER)).andRespond(withSuccess());

        builder.build().get().uri("http://inventory/stock").retrieve().toBodilessEntity();

        server.verify();
    }
}
