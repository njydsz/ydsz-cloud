package com.njydsz.pmis.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextInitializedEvent;
import org.springframework.core.env.ConfigurableEnvironment;

import com.njydsz.pmis.common.config.encrypt.ConfigEncryptor;
import com.njydsz.pmis.common.config.encrypt.EncryptablePropertyResolver;

/**
 * 配置加密自动配置
 *
 * <p>在 ApplicationEnvironmentPreparedEvent 阶段（早于 Bean 创建）
 * 解密所有 ENC(...) 格式的配置值，确保后续 Bean 注入的是明文。
 *
 * <p>密钥来源优先级：
 * <ol>
 *   <li>环境变量 {@code PMIS_CONFIG_ENCRYPT_KEY}</li>
 *   <li>配置属性 {@code pmis.config.encrypt.secret-key}</li>
 *   <li>默认开发密钥（仅当 enabled=true 且未设置密钥时警告）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(ConfigProperties.class)
@ConditionalOnProperty(prefix = "pmis.config.encrypt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConfigAutoConfiguration implements ApplicationListener<ApplicationContextInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ConfigAutoConfiguration.class);

    @Override
    public void onApplicationEvent(ApplicationContextInitializedEvent event) {
        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();

        String secretKey = resolveSecretKey(environment);
        if (secretKey == null || secretKey.isBlank()) {
            log.warn("Config encryption is enabled but no secret key found. Skipping decryption.");
            return;
        }

        ConfigEncryptor encryptor = new ConfigEncryptor(secretKey);
        EncryptablePropertyResolver resolver = new EncryptablePropertyResolver(encryptor);
        resolver.resolveEncryptedProperties(environment);
    }

    private String resolveSecretKey(ConfigurableEnvironment environment) {
        // 1. 环境变量
        String envName = environment.getProperty("pmis.config.encrypt.secret-key-env", "PMIS_CONFIG_ENCRYPT_KEY");
        String envKey = System.getenv(envName);
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }

        // 2. 配置属性
        String configKey = environment.getProperty("pmis.config.encrypt.secret-key");
        if (configKey != null && !configKey.isBlank()) {
            return configKey;
        }

        // 3. 默认开发密钥
        String defaultKey = environment.getProperty("pmis.config.encrypt.default-key", "pmis-dev-secret-key-change-in-prod");
        if ("pmis-dev-secret-key-change-in-prod".equals(defaultKey)) {
            log.warn("Using default development encryption key. Set PMIS_CONFIG_ENCRYPT_KEY environment variable in production!");
        }
        return defaultKey;
    }
}
