package com.chainflow.auth.api;

import com.chainflow.auth.config.JwtConfig;
import com.chainflow.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig(classes = {JwtConfig.class, AuthController.class})
@TestPropertySource(properties = {
        "chainflow.security.jwt-secret=change-this-chainflow-secret-key-please-32chars",
        "chainflow.security.expiration-seconds=1800",
        "chainflow.demo.username=operator",
        "chainflow.demo.password=test-password"
})
class AuthControllerTest {
    @Autowired
    private AuthController controller;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    void loginReturnsConfiguredTokenExpiration() {
        var response = controller.login(new AuthController.LoginRequest("operator", "test-password"));

        assertEquals("0", response.code());
        assertNotNull(response.data());
        assertEquals(1_800, response.data().expiresIn());
        assertEquals("Bearer", response.data().tokenType());
        assertNotNull(response.data().accessToken());

        var claims = tokenProvider.parse(response.data().accessToken());
        assertEquals(response.data().expiresIn(),
                (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1_000);
    }
}
