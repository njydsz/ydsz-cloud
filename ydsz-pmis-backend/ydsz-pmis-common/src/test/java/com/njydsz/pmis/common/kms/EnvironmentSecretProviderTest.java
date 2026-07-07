package com.njydsz.pmis.common.kms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EnvironmentSecretProvider 单元测试
 *
 * <p>验证密钥获取优先级：环境变量 > Nacos 配置（KmsProperties.secrets）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("EnvironmentSecretProvider 测试")
class EnvironmentSecretProviderTest {

    private KmsProperties kmsProperties;
    private Environment environment;
    private EnvironmentSecretProvider provider;

    @BeforeEach
    void setUp() {
        kmsProperties = new KmsProperties();
        environment = mock(Environment.class);
        provider = new EnvironmentSecretProvider(kmsProperties, environment);
    }

    // ==================== 环境变量读取 ====================

    @Test
    @DisplayName("环境变量存在时优先返回环境变量值")
    void getSecret_shouldReturnEnvValueWhenPresent() {
        String secretKey = "db.password";
        String envVarName = "PMIS_SECRETS_DB_PASSWORD";
        when(environment.getProperty(envVarName)).thenReturn("env-db-password");

        String result = provider.getSecret(secretKey);

        assertEquals("env-db-password", result);
    }

    @Test
    @DisplayName("环境变量名为空时回退到 Nacos 配置")
    void getSecret_shouldFallbackToConfigWhenEnvEmpty() {
        String secretKey = "db.password";
        when(environment.getProperty("PMIS_SECRETS_DB_PASSWORD")).thenReturn("");
        Map<String, String> secrets = new HashMap<>();
        secrets.put(secretKey, "nacos-db-password");
        kmsProperties.setSecrets(secrets);

        String result = provider.getSecret(secretKey);

        assertEquals("nacos-db-password", result);
    }

    @Test
    @DisplayName("环境变量名为 null 时回退到 Nacos 配置")
    void getSecret_shouldFallbackToConfigWhenEnvNull() {
        String secretKey = "redis.password";
        when(environment.getProperty("PMIS_SECRETS_REDIS_PASSWORD")).thenReturn(null);
        Map<String, String> secrets = new HashMap<>();
        secrets.put(secretKey, "nacos-redis-password");
        kmsProperties.setSecrets(secrets);

        String result = provider.getSecret(secretKey);

        assertEquals("nacos-redis-password", result);
    }

    // ==================== Nacos 配置读取 ====================

    @Test
    @DisplayName("环境变量不存在时从 Nacos 配置读取")
    void getSecret_shouldReturnConfigValueWhenEnvAbsent() {
        String secretKey = "jwt.secret";
        when(environment.getProperty("PMIS_SECRETS_JWT_SECRET")).thenReturn(null);
        Map<String, String> secrets = new HashMap<>();
        secrets.put(secretKey, "jwt-secret-value");
        kmsProperties.setSecrets(secrets);

        String result = provider.getSecret(secretKey);

        assertEquals("jwt-secret-value", result);
    }

    @Test
    @DisplayName("Nacos 配置值为空字符串时返回 null")
    void getSecret_shouldReturnNullWhenConfigValueEmpty() {
        String secretKey = "db.password";
        when(environment.getProperty("PMIS_SECRETS_DB_PASSWORD")).thenReturn(null);
        Map<String, String> secrets = new HashMap<>();
        secrets.put(secretKey, "");
        kmsProperties.setSecrets(secrets);

        String result = provider.getSecret(secretKey);

        assertNull(result);
    }

    // ==================== 不存在的密钥 ====================

    @Test
    @DisplayName("密钥不存在时返回 null")
    void getSecret_shouldReturnNullWhenKeyAbsent() {
        when(environment.getProperty("PMIS_SECRETS_DB_PASSWORD")).thenReturn(null);
        kmsProperties.setSecrets(new HashMap<>());

        String result = provider.getSecret("db.password");

        assertNull(result);
    }

    @Test
    @DisplayName("secrets Map 为 null 时返回 null")
    void getSecret_shouldReturnNullWhenSecretsMapNull() {
        when(environment.getProperty("PMIS_SECRETS_DB_PASSWORD")).thenReturn(null);
        kmsProperties.setSecrets(null);

        String result = provider.getSecret("db.password");

        assertNull(result);
    }

    // ==================== 默认值 ====================

    @Test
    @DisplayName("密钥不存在时返回默认值")
    void getSecretWithDefault_shouldReturnDefaultWhenAbsent() {
        when(environment.getProperty("PMIS_SECRETS_DB_PASSWORD")).thenReturn(null);
        kmsProperties.setSecrets(new HashMap<>());

        String result = provider.getSecret("db.password", "default-pwd");

        assertEquals("default-pwd", result);
    }

    @Test
    @DisplayName("密钥存在时返回实际值而非默认值")
    void getSecretWithDefault_shouldReturnValueWhenPresent() {
        String secretKey = "db.password";
        when(environment.getProperty("PMIS_SECRETS_DB_PASSWORD")).thenReturn("actual-pwd");

        String result = provider.getSecret(secretKey, "default-pwd");

        assertEquals("actual-pwd", result);
    }

    // ==================== 环境变量名转换 ====================

    @Test
    @DisplayName("环境变量名转换：db.password → PMIS_SECRETS_DB_PASSWORD")
    void toEnvVarName_shouldConvertCorrectly() {
        String envVarName = provider.toEnvVarName("db.password");

        assertEquals("PMIS_SECRETS_DB_PASSWORD", envVarName);
    }

    @Test
    @DisplayName("环境变量名转换：jwt.secret → PMIS_SECRETS_JWT_SECRET")
    void toEnvVarName_shouldConvertJwtSecret() {
        String envVarName = provider.toEnvVarName("jwt.secret");

        assertEquals("PMIS_SECRETS_JWT_SECRET", envVarName);
    }
}
