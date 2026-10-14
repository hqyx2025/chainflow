package com.chainflow.auth.api;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.common.security.JwtTokenProvider;
import com.chainflow.common.security.RevocationService;
import com.chainflow.common.security.RevocationStoreUnavailableException;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final JwtTokenProvider tokenProvider;
    private final String demoUsername;
    private final String demoPassword;
    private final long expirationSeconds;
    private final RevocationService revocationService;

    public AuthController(JwtTokenProvider tokenProvider,
                          @Value("${chainflow.demo.username:admin}") String demoUsername,
                          @Value("${chainflow.demo.password:admin123}") String demoPassword,
                          @Value("${chainflow.security.expiration-seconds:7200}") long expirationSeconds,
                          ObjectProvider<RevocationService> revocationServiceProvider) {
        this.tokenProvider = tokenProvider;
        this.demoUsername = demoUsername;
        this.demoPassword = demoPassword;
        this.expirationSeconds = expirationSeconds;
        this.revocationService = revocationServiceProvider.getIfAvailable();
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        if (!demoUsername.equals(request.username()) || !demoPassword.equals(request.password())) {
            return ApiResponse.failure("AUTH-001", "用户名或密码错误", null);
        }
        return ApiResponse.success(new LoginResponse(
                tokenProvider.createToken(request.username(), "SUPPLY_CHAIN_OPERATOR"),
                "Bearer", expirationSeconds));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.length() <= 7) {
            return unauthorized("缺少有效的 Bearer Token");
        }
        String token = authorization.substring(7).trim();
        try {
            if (revocationService == null) {
                return unavailable();
            }
            revocationService.revoke(token);
            return ResponseEntity.ok(ApiResponse.success(null));
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized("JWT 无效或已过期");
        } catch (RevocationStoreUnavailableException ex) {
            return unavailable();
        }
    }

    private ResponseEntity<ApiResponse<Void>> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("401", message, null));
    }

    private ResponseEntity<ApiResponse<Void>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure("503", "JWT 注销服务暂不可用，请稍后重试", null));
    }

    @GetMapping("/health")
    public ApiResponse<String> health() { return ApiResponse.success("auth-service is ready"); }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}
}
