package com.njydsz.pmis.auth.token;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtTokenProvider 单元测试
 */
@DisplayName("JwtTokenProvider JWT 工具测试")
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret",
                "pmis-default-jwt-secret-key-please-change-in-production-environment-must-be-256-bits");
        ReflectionTestUtils.setField(provider, "issuer", "pmis-test");
        ReflectionTestUtils.invokeMethod(provider, "init");
    }

    @Test
    @DisplayName("generateToken 应生成可解析的 Token")
    void generateToken_parse() {
        String token = provider.generateToken(100L, "zhangsan", 3600L);
        assertThat(token).isNotBlank();
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("getUserId / getUsername 应能正确解析")
    void getClaims() {
        String token = provider.generateToken(200L, "lisi", 3600L);
        assertThat(provider.getUserId(token)).isEqualTo(200L);
        assertThat(provider.getUsername(token)).isEqualTo("lisi");
    }

    @Test
    @DisplayName("generateRefreshToken 应能被 validate")
    void refreshToken() {
        String rt = provider.generateRefreshToken(300L, 86400L);
        assertThat(provider.validateToken(rt)).isTrue();
    }

    @Test
    @DisplayName("Claims 中应包含 type=access")
    void claimsAccess() {
        String token = provider.generateToken(1L, "u", 60L);
        Claims claims = provider.parseClaims(token);
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        assertThat(claims.getIssuer()).isEqualTo("pmis-test");
    }

    @Test
    @DisplayName("Claims 中应包含 type=refresh")
    void claimsRefresh() {
        String rt = provider.generateRefreshToken(1L, 60L);
        Claims claims = provider.parseClaims(rt);
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("非法 Token validateToken 应返回 false")
    void invalidToken() {
        assertThat(provider.validateToken("not-a-valid-token")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
    }
}
