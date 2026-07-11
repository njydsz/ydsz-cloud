package com.njydsz.pmis.common.token;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JwtTokenProvider} 单元测试
 *
 * <p>覆盖 Token 生成/验证/解析、密钥强度校验、弱密钥生产环境拦截等核心安全逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JwtTokenProvider JWT Token 工具测试")
class JwtTokenProviderTest {

    private static final String VALID_SECRET = "this-is-a-strong-test-secret-key-32+bytes!";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", VALID_SECRET);
        ReflectionTestUtils.setField(provider, "issuer", "pmis-test");
        ReflectionTestUtils.setField(provider, "accessExpireSeconds", 7200L);
        ReflectionTestUtils.setField(provider, "refreshExpireSeconds", 604800L);
        ReflectionTestUtils.setField(provider, "activeProfile", "test");
        provider.init();
    }

    // ==================== Token 生成与验证 ====================

    @Nested
    @DisplayName("Token 生成与验证")
    class GenerateAndValidateTest {

        @Test
        @DisplayName("生成 Token 后应能验证通过")
        void shouldValidateGeneratedToken() {
            String token = provider.generateToken("user-123", "admin",
                    List.of("ADMIN"), List.of("READ", "WRITE"), 3600L);

            assertNotNull(token);
            assertTrue(provider.validateToken(token));
        }

        @Test
        @DisplayName("生成的 Token 应包含正确的 Claims")
        void shouldContainCorrectClaims() {
            String token = provider.generateToken("user-123", "admin",
                    List.of("ADMIN"), List.of("READ", "WRITE"), 3600L);

            Claims claims = provider.parseClaims(token);
            assertEquals("user-123", claims.getSubject());
            assertEquals("admin", claims.get("username", String.class));
            assertEquals("access", claims.get("type", String.class));
            assertEquals("pmis-test", claims.getIssuer());
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get("roles");
            assertEquals(List.of("ADMIN"), roles);
        }

        @Test
        @DisplayName("包含完整数据权限上下文的 Token")
        void shouldGenerateTokenWithDataScope() {
            String token = provider.generateToken("user-123", "admin",
                    List.of("ADMIN"), List.of("READ"),
                    "dept-1", List.of("dept-1", "dept-2"), List.of("dept-3"),
                    "DEPT_AND_CHILD", 3600L);

            Claims claims = provider.parseClaims(token);
            assertEquals("dept-1", claims.get("deptId", String.class));
            assertEquals("DEPT_AND_CHILD", claims.get("dataScope", String.class));
            @SuppressWarnings("unchecked")
            List<String> deptIds = (List<String>) claims.get("deptIds");
            assertEquals(List.of("dept-1", "dept-2"), deptIds);
        }

        @Test
        @DisplayName("null roles 和 permissions 时 Token 仍有效")
        void shouldHandleNullRolesAndPermissions() {
            String token = provider.generateToken("user-123", "admin",
                    null, null, 3600L);

            assertTrue(provider.validateToken(token));
            assertTrue(provider.getRoles(token).isEmpty());
            assertTrue(provider.getPermissions(token).isEmpty());
        }

        @Test
        @DisplayName("刷新 Token 的 type 为 refresh")
        void shouldGenerateRefreshTokenWithCorrectType() {
            String refreshToken = provider.generateRefreshToken("user-123", 86400L);

            Claims claims = provider.parseClaims(refreshToken);
            assertEquals("refresh", claims.get("type", String.class));
            assertEquals("user-123", claims.getSubject());
        }

        @Test
        @DisplayName("过期 Token 验证失败")
        void shouldFailValidationForExpiredToken() {
            // 生成一个已过期的 Token（expireSeconds=0 表示立即过期）
            String token = provider.generateToken("user-123", "admin",
                    null, null, 0L);

            // Token 可能在生成后立即过期
            assertFalse(provider.validateToken(token));
        }
    }

    // ==================== Token 解析方法 ====================

    @Nested
    @DisplayName("Token 解析方法")
    class ParseMethodsTest {

        @Test
        @DisplayName("getUserId 从 Token 提取用户 ID")
        void shouldExtractUserId() {
            String token = provider.generateToken("user-123", "admin",
                    null, null, 3600L);
            assertEquals("user-123", provider.getUserId(token));
        }

        @Test
        @DisplayName("getUsername 从 Token 提取用户名")
        void shouldExtractUsername() {
            String token = provider.generateToken("user-123", "admin",
                    null, null, 3600L);
            assertEquals("admin", provider.getUsername(token));
        }

        @Test
        @DisplayName("getRoles 从 Token 提取角色列表")
        void shouldExtractRoles() {
            String token = provider.generateToken("user-123", "admin",
                    List.of("ADMIN", "MANAGER"), null, 3600L);
            assertEquals(List.of("ADMIN", "MANAGER"), provider.getRoles(token));
        }

        @Test
        @DisplayName("getPermissions 从 Token 提取权限列表")
        void shouldExtractPermissions() {
            String token = provider.generateToken("user-123", "admin",
                    null, List.of("READ", "WRITE", "DELETE"), 3600L);
            assertEquals(List.of("READ", "WRITE", "DELETE"), provider.getPermissions(token));
        }

        @Test
        @DisplayName("getRemainingExpirationSeconds 返回正数（未过期 Token）")
        void shouldReturnPositiveRemainingSeconds() {
            String token = provider.generateToken("user-123", "admin",
                    null, null, 3600L);
            long remaining = provider.getRemainingExpirationSeconds(token);
            assertTrue(remaining > 0 && remaining <= 3600);
        }

        @Test
        @DisplayName("无效 Token 的 getRemainingExpirationSeconds 返回 0")
        void shouldReturnZeroForInvalidToken() {
            assertEquals(0, provider.getRemainingExpirationSeconds("invalid.token.here"));
        }
    }

    // ==================== 密钥校验 ====================

    @Nested
    @DisplayName("密钥强度校验")
    class KeyValidationTest {

        @Test
        @DisplayName("空密钥抛 IllegalStateException")
        void shouldThrowForEmptySecret() {
            JwtTokenProvider p = new JwtTokenProvider();
            ReflectionTestUtils.setField(p, "secret", "");
            ReflectionTestUtils.setField(p, "activeProfile", "dev");

            assertThrows(IllegalStateException.class, p::init);
        }

        @Test
        @DisplayName("短密钥（<32 字节）抛 IllegalStateException")
        void shouldThrowForShortSecret() {
            JwtTokenProvider p = new JwtTokenProvider();
            ReflectionTestUtils.setField(p, "secret", "short-key");
            ReflectionTestUtils.setField(p, "activeProfile", "dev");

            assertThrows(IllegalStateException.class, p::init);
        }

        @Test
        @DisplayName("弱密钥在非生产环境允许启动但标记 defaultKeyUsed")
        void shouldAllowWeakSecretInNonProdProfile() {
            JwtTokenProvider p = new JwtTokenProvider();
            // 使用包含弱标识但长度足够的密钥
            ReflectionTestUtils.setField(p, "secret", "default-secret-key-at-least-32-bytes!!");
            ReflectionTestUtils.setField(p, "activeProfile", "dev");
            p.init();

            assertTrue(p.isDefaultKeyUsed());
        }

        @Test
        @DisplayName("弱密钥在生产环境拒绝启动")
        void shouldRejectWeakSecretInProdProfile() {
            JwtTokenProvider p = new JwtTokenProvider();
            ReflectionTestUtils.setField(p, "secret", "default-secret-key-at-least-32-bytes!!");
            ReflectionTestUtils.setField(p, "activeProfile", "prod");

            assertThrows(IllegalStateException.class, p::init);
        }

        @Test
        @DisplayName("Base64 编码密钥正确解析")
        void shouldParseBase64Secret() {
            JwtTokenProvider p = new JwtTokenProvider();
            // 生成 32 字节的 Base64 编码密钥
            String base64Key = "base64:" + java.util.Base64.getEncoder()
                    .encodeToString("0123456789ABCDEF0123456789ABCDEF".getBytes());
            ReflectionTestUtils.setField(p, "secret", base64Key);
            ReflectionTestUtils.setField(p, "activeProfile", "dev");
            p.init();

            assertNotNull(p);
        }

        @Test
        @DisplayName("isWeakSecret 静态方法正确识别弱密钥")
        void shouldIdentifyWeakSecrets() {
            assertTrue(JwtTokenProvider.isWeakSecret("default"));
            assertTrue(JwtTokenProvider.isWeakSecret("change-me"));
            assertTrue(JwtTokenProvider.isWeakSecret("your-secret-key"));
            assertTrue(JwtTokenProvider.isWeakSecret("test-secret"));
            assertTrue(JwtTokenProvider.isWeakSecret(null));
            assertTrue(JwtTokenProvider.isWeakSecret(""));

            // 强密钥
            assertFalse(JwtTokenProvider.isWeakSecret("a8s7df6a9s8d7f6a9s8d7f6a9s8d7f6a"));
        }

        @Test
        @DisplayName("isProductionProfile 静态方法正确识别生产环境")
        void shouldIdentifyProdProfiles() {
            assertTrue(JwtTokenProvider.isProductionProfile("prod"));
            assertTrue(JwtTokenProvider.isProductionProfile("production"));
            assertTrue(JwtTokenProvider.isProductionProfile("prod-cn"));

            assertFalse(JwtTokenProvider.isProductionProfile("dev"));
            assertFalse(JwtTokenProvider.isProductionProfile("sit"));
            assertFalse(JwtTokenProvider.isProductionProfile("uat"));
            assertFalse(JwtTokenProvider.isProductionProfile(null));
            assertFalse(JwtTokenProvider.isProductionProfile(""));
        }
    }
}
