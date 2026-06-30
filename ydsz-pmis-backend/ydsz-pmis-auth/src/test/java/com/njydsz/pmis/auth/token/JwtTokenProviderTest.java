package com.njydsz.pmis.auth.token;

import com.njydsz.pmis.common.token.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider 单元测试
 */
@DisplayName("JwtTokenProvider JWT 工具测试")
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        // 32 字节的安全测试密钥 (Base64 形式)
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < keyBytes.length; i++) keyBytes[i] = (byte) (i + 1);
        ReflectionTestUtils.setField(provider, "secret", "base64:" + Base64.getEncoder().encodeToString(keyBytes));
        ReflectionTestUtils.setField(provider, "issuer", "pmis-test");
        ReflectionTestUtils.setField(provider, "accessExpireSeconds", 7200L);
        ReflectionTestUtils.setField(provider, "refreshExpireSeconds", 604800L);
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

    @Test
    @DisplayName("roles / permissions 应能从 Claims 读出")
    void rolesAndPermissions() {
        String token = provider.generateToken(1L, "u", List.of("admin", "ops"), List.of("a:b:c", "x:y:z"), 60L);
        assertThat(provider.getRoles(token)).containsExactly("admin", "ops");
        assertThat(provider.getPermissions(token)).containsExactly("a:b:c", "x:y:z");
    }

    @Test
    @DisplayName("密钥不足 32 字节应在 init 时抛 IllegalStateException")
    void init_shortSecret() {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "secret", "short-secret");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(p, "init"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少 32 字节");
    }

    @Test
    @DisplayName("密钥为空应在 init 时抛 IllegalStateException")
    void init_emptySecret() {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "secret", "");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(p, "init"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("base64: 前缀密钥解析失败应在 init 时抛 IllegalStateException")
    void init_badBase64() {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "secret", "base64:!!not-valid-base64!!");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(p, "init"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("默认密钥标记应触发警告但允许启动")
    void init_defaultKeyMarker() {
        JwtTokenProvider p = new JwtTokenProvider();
        // 32 字节但包含 default 关键字的密钥
        String s = "pmis-default-jwt-secret-key-aaaaaaaaaaaaaaaaaaaaaa";
        ReflectionTestUtils.setField(p, "secret", s);
        ReflectionTestUtils.invokeMethod(p, "init");
        assertThat(p.isDefaultKeyUsed()).isTrue();
    }

    @Test
    @DisplayName("自定义密钥应使 isDefaultKeyUsed = false")
    void init_customKey() {
        assertThat(provider.isDefaultKeyUsed()).isFalse();
    }

    @Test
    @DisplayName("getAccessExpireSeconds / getRefreshExpireSeconds 应返回配置值")
    void expireConfig() {
        ReflectionTestUtils.setField(provider, "accessExpireSeconds", 60L);
        ReflectionTestUtils.setField(provider, "refreshExpireSeconds", 120L);
        assertThat(provider.getAccessExpireSeconds()).isEqualTo(60L);
        assertThat(provider.getRefreshExpireSeconds()).isEqualTo(120L);
    }

    @Test
    @DisplayName("新签名: 显式 expireSeconds=null 应回落到配置默认值")
    void newSignature_nullExpire() {
        String token = provider.generateToken(1L, "u", null, null, null);
        assertThat(provider.validateToken(token)).isTrue();
    }
}
