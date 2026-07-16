package com.njydsz.pmis.common.config.health;

import java.util.HashSet;
import java.util.Set;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * 配置加密健康指标
 *
 * <p>检查 Jasypt 配置加密的运行时状态：
 * <ul>
 *   <li>主密码是否已配置（通过环境变量 {@code JASYPT_ENCRYPTOR_PASSWORD} 或
 *       {@code jasypt.encryptor.password} 属性）</li>
 *   <li>环境中是否存在 {@code ENC()} 格式的加密属性</li>
 *   <li>密钥来源（环境变量 / 配置属性 / 未配置）</li>
 * </ul>
 *
 * <h3>健康状态</h3>
 * <ul>
 *   <li><b>UP</b>：主密码已配置，或无加密属性（无需解密）</li>
 *   <li><b>DOWN</b>：存在 ENC() 加密属性但主密码未配置</li>
 *   <li><b>UNKNOWN</b>：环境不可用</li>
 * </ul>
 *
 * <h3>暴露信息</h3>
 * <pre>{
 *   "status": "UP",
 *   "details": {
 *     "encryptorPasswordSource": "ENV_VARIABLE",
 *     "encryptedPropertyCount": 3,
 *     "encryptedProperties": ["spring.datasource.password", "spring.data.redis.password", "pmis.jwt.secret"]
 *   }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public class ConfigEncryptHealthIndicator implements HealthIndicator {

    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";
    private static final String JASYPT_PASSWORD_ENV = "JASYPT_ENCRYPTOR_PASSWORD";
    private static final String JASYPT_PASSWORD_PROPERTY = "jasypt.encryptor.password";

    /** 脱敏时仅显示属性名的最后一段（如 spring.datasource.password → password） */
    private static final int MAX_DETAIL_ITEMS = 20;

    private final ConfigurableEnvironment environment;

    public ConfigEncryptHealthIndicator(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();

        // 检查密钥来源
        String keySource = resolveKeySource();
        int encryptedCount = countEncryptedProperties();
        Set<String> encryptedKeys = findEncryptedPropertyKeys();

        if (encryptedCount == 0) {
            builder.up().withDetail("encryptorPasswordSource", keySource);
            builder.withDetail("encryptedPropertyCount", 0);
        } else if ("NOT_CONFIGURED".equals(keySource)) {
            builder.down();
            builder.withDetail("error", "Encrypted properties found but Jasypt master password is not configured");
        } else {
            builder.up().withDetail("encryptorPasswordSource", keySource);
            builder.withDetail("encryptedPropertyCount", encryptedCount);
            // 仅显示前 MAX_DETAIL_ITEMS 个属性名
            Set<String> displayKeys = new HashSet<>();
            int i = 0;
            for (String key : encryptedKeys) {
                if (i++ >= MAX_DETAIL_ITEMS) {
                    displayKeys.add("... (" + (encryptedKeys.size() - MAX_DETAIL_ITEMS) + " more)");
                    break;
                }
                displayKeys.add(maskKey(key));
            }
            builder.withDetail("encryptedProperties", displayKeys);
        }

        return builder.build();
    }

    /**
     * 判断主密码的来源
     *
     * @return "ENV_VARIABLE" / "CONFIG_PROPERTY" / "NOT_CONFIGURED"
     */
    private String resolveKeySource() {
        String envKey = System.getenv(JASYPT_PASSWORD_ENV);
        if (envKey != null && !envKey.isBlank()) {
            return "ENV_VARIABLE";
        }

        String configKey = environment.getProperty(JASYPT_PASSWORD_PROPERTY);
        if (configKey != null && !configKey.isBlank()) {
            return "CONFIG_PROPERTY";
        }

        return "NOT_CONFIGURED";
    }

    /**
     * 统计 ENC() 格式的属性数量
     */
    private int countEncryptedProperties() {
        int count = 0;
        for (PropertySource<?> ps : environment.getPropertySources()) {
            if (ps instanceof EnumerablePropertySource<?> enumerable) {
                for (String key : enumerable.getPropertyNames()) {
                    Object value = enumerable.getProperty(key);
                    if (value instanceof String strValue && isEncrypted(strValue)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * 查找所有 ENC() 格式属性的键名集合
     */
    private Set<String> findEncryptedPropertyKeys() {
        Set<String> keys = new HashSet<>();
        for (PropertySource<?> ps : environment.getPropertySources()) {
            if (ps instanceof EnumerablePropertySource<?> enumerable) {
                for (String key : enumerable.getPropertyNames()) {
                    Object value = enumerable.getProperty(key);
                    if (value instanceof String strValue && isEncrypted(strValue)) {
                        keys.add(key);
                    }
                }
            }
        }
        return keys;
    }

    private boolean isEncrypted(String value) {
        return value != null
                && value.startsWith(ENC_PREFIX)
                && value.endsWith(ENC_SUFFIX);
    }

    /**
     * 脱敏属性名：仅保留最后一段
     * <p>如 spring.datasource.password → ***.password
     */
    private String maskKey(String key) {
        int lastDot = key.lastIndexOf('.');
        if (lastDot < 0) {
            return "***";
        }
        return "***" + key.substring(lastDot);
    }
}
