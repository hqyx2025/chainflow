package com.chainflow.common.web;

import org.slf4j.MDC;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/** Adds the inbound request trace id to all service-to-service RestClient calls. */
@Component
public class TraceIdRestClientCustomizer implements RestClientCustomizer {
    @Override
    public void customize(RestClient.Builder builder) {
        builder.requestInterceptor(new TraceIdInterceptor());
    }

    static final class TraceIdInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            String traceId = MDC.get(TraceIds.HEADER);
            if (StringUtils.hasText(traceId) && !request.getHeaders().containsKey(TraceIds.HEADER)) {
                request.getHeaders().set(TraceIds.HEADER, traceId);
            }
            return execution.execute(request, body);
        }
    }
}
