package com.chainflow.common.web;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.common.exception.BusinessException;
import com.chainflow.common.exception.GlobalErrorCode;
import com.chainflow.common.security.RevocationStoreUnavailableException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
        return ResponseEntity.status(409).body(ApiResponse.failure(ex.getCode(), ex.getMessage(), traceId()));
    }

    @ExceptionHandler(RevocationStoreUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> revocationUnavailable(RevocationStoreUnavailableException ex) {
        return ResponseEntity.status(503).body(ApiResponse.failure("503", ex.getMessage(), traceId()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(GlobalErrorCode.INVALID_ARGUMENT, "请求参数不合法", traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        return ResponseEntity.internalServerError().body(ApiResponse.failure(GlobalErrorCode.INTERNAL_ERROR, "服务内部错误", traceId()));
    }

    private String traceId() { return MDC.get(TraceIdFilter.TRACE_ID_HEADER); }
}
