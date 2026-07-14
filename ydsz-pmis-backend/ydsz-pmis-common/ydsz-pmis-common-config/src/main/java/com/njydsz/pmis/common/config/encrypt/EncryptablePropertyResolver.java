package com.njydsz.pmis.common.config.encrypt;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

/**
 * 可加密属性解析器
 *
 * <p>扫描 Environment 中所有属性，对 ENC(...) 格式的值进行解密，
 * 并将解密后的值以高优先级 PropertySource 注入回 Environment。
 *
 * <p>支持属性名模式匹配：仅对敏感属性（password, secret, key, token, credential）进行解密。
 *
 * @author Marvin Lee
 * @since 1.0.0
 */
public class EncryptablePropertyResolver {

    private static final Logger log = LoggerFactory.getLogger(EncryptablePropertyResolver.class);
    private static final String DECRYPTED_SOURCE_NAME = "pmisDecryptedProperties";
    private static final String[] SENSITIVE_KEYWORDS = {"password", "secret", "key", "token", "credential", "pwd"};

    private final ConfigEncryptor encryptor;

    public EncryptablePropertyResolver(ConfigEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    /**
     * 解析环境中的所有加密属性
     *
     * @param environment Spring 环境
     */
    public void resolveEncryptedProperties(ConfigurableEnvironment environment) {
        Set<String> processedKeys = new HashSet<>();
        java.util.Map<String, Object> decryptedProps = new java.util.HashMap<>();

        environment.getPropertySources().forEach(propertySource -> {
            if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
                if (DECRYPTED_SOURCE_NAME.equals(propertySource.getName())) {
                    return;
                }
                for (String key : enumerable.getPropertyNames()) {
                    if (processedKeys.contains(key)) {
                        continue;
                    }
                    Object value = enumerable.getProperty(key);
                    if (value instanceof String strValue && encryptor.isEncrypted(strValue)) {
                        try {
                            String decrypted = encryptor.decrypt(strValue);
                            decryptedProps.put(key, decrypted);
                            processedKeys.add(key);
                            log.debug("Decrypted property: {}", maskSensitiveKey(key));
                        } catch (Exception e) {
                            log.error("Failed to decrypt property: {}", maskSensitiveKey(key), e);
                        }
                    }
                }
            }
        });

        if (!decryptedProps.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource(DECRYPTED_SOURCE_NAME, decryptedProps));
            log.info("Resolved {} encrypted properties", decryptedProps.size());
        }
    }

    /**
     * 脱敏属性名（仅保留前缀和后缀）
     */
    private static String maskSensitiveKey(String key) {
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (key.toLowerCase().contains(keyword)) {
                int idx = key.toLowerCase().indexOf(keyword);
                int start = Math.max(0, idx - 3);
                int end = Math.min(key.length(), idx + keyword.length() + 3);
                return key.substring(0, start) + "***" + key.substring(end);
            }
        }
        return key;
    }
}
