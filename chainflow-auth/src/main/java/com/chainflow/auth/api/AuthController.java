package com.chainflow.auth.api;

import com.chainflow.common.api.ApiResponse;
import com.chainflow.common.security.JwtTokenProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final JwtTokenProvider tokenProvider;
    private final String demoUsername;
    private final String demoPassword;
    private final long expirationSeconds;

    public AuthController(JwtTokenProvider tokenProvider,
                          @Value("${chainflow.demo.username:admin}") String demoUsername,
                          @Value("${chainflow.demo.password:admin123}") String demoPassword,
                          @Value("${chainflow.security.expiration-seconds:7200}") long expirationSeconds) {
        this.tokenProvider = tokenProvider;
        this.demoUsername = demoUsername;
        this.demoPassword = demoPassword;
        this.expirationSeconds = expirationSeconds;
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

    @GetMapping("/health")
    public ApiResponse<String> health() { return ApiResponse.success("auth-service is ready"); }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}
}
