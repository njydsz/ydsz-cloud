package com.njydsz.pmis.common.kms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SecretManager 单元测试
 *
 * <p>验证密钥管理器正确委托给 {@link SecretProvider}，并提供常用密钥便捷方法。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SecretManager 测试")
class SecretManagerTest {

    private SecretProvider secretProvider;
    private SecretManager secretManager;

    @BeforeEach
    void setUp() {
        secretProvider = mock(SecretProvider.class);
        secretManager = new SecretManager(secretProvider);
    }

    // ==================== 委托验证 ====================

    @Test
    @DisplayName("getSecret 应委托给 SecretProvider")
    void getSecret_shouldDelegateToProvider() {
        when(secretProvider.getSecret("db.password")).thenReturn("db-pwd");

        String result = secretManager.getSecret("db.password");

        assertEquals("db-pwd", result);
    }

    @Test
    @DisplayName("getSecret 带默认值应委托给 SecretProvider")
    void getSecretWithDefault_shouldDelegateToProvider() {
        when(secretProvider.getSecret("db.password", "default")).thenReturn("db-pwd");

        String result = secretManager.getSecret("db.password", "default");

        assertEquals("db-pwd", result);
    }

    // ==================== 便捷方法 ====================

    @Test
    @DisplayName("getDbPassword 应返回 db.password 密钥")
    void getDbPassword_shouldReturnDbPassword() {
        when(secretProvider.getSecret("db.password")).thenReturn("db-pwd");

        String result = secretManager.getDbPassword();

        assertEquals("db-pwd", result);
    }

    @Test
    @DisplayName("getRedisPassword 应返回 redis.password 密钥")
    void getRedisPassword_shouldReturnRedisPassword() {
        when(secretProvider.getSecret("redis.password")).thenReturn("redis-pwd");

        String result = secretManager.getRedisPassword();

        assertEquals("redis-pwd", result);
    }

    @Test
    @DisplayName("getJwtSecret 应返回 jwt.secret 密钥")
    void getJwtSecret_shouldReturnJwtSecret() {
        when(secretProvider.getSecret("jwt.secret")).thenReturn("jwt-secret-value");

        String result = secretManager.getJwtSecret();

        assertEquals("jwt-secret-value", result);
    }

    // ==================== 默认值场景 ====================

    @Test
    @DisplayName("密钥不存在时应返回 null")
    void getDbPassword_shouldReturnNullWhenAbsent() {
        when(secretProvider.getSecret("db.password")).thenReturn(null);

        String result = secretManager.getDbPassword();

        assertNull(result);
    }

    @Test
    @DisplayName("带默认值获取密钥 - 密钥不存在时返回默认值")
    void getSecretWithDefault_shouldReturnDefaultWhenAbsent() {
        when(secretProvider.getSecret("db.password", "fallback")).thenReturn("fallback");

        String result = secretManager.getSecret("db.password", "fallback");

        assertEquals("fallback", result);
    }

    @Test
    @DisplayName("带默认值获取密钥 - 密钥存在时返回实际值")
    void getSecretWithDefault_shouldReturnActualWhenPresent() {
        when(secretProvider.getSecret("jwt.secret", "default-secret")).thenReturn("actual-secret");

        String result = secretManager.getSecret("jwt.secret", "default-secret");

        assertEquals("actual-secret", result);
    }

    // ==================== 密钥标识常量 ====================

    @Test
    @DisplayName("密钥标识常量应与便捷方法使用的一致")
    void keyConstants_shouldMatchExpectedValues() {
        assertEquals("db.password", SecretManager.KEY_DB_PASSWORD);
        assertEquals("redis.password", SecretManager.KEY_REDIS_PASSWORD);
        assertEquals("jwt.secret", SecretManager.KEY_JWT_SECRET);
    }
}
