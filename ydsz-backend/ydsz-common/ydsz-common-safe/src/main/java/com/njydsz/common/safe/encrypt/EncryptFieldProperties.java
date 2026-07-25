package com.njydsz.common.safe.encrypt;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 字段加密配置属性
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   safe:
 *     field-encryption:
 *       enabled: true
 *       default-key-version: 2
 *       keys:
 *         1: "base64-encoded-32-byte-key..."
 *         2: "base64-encoded-32-byte-key..."
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.field-encryption")
public class EncryptFieldProperties {

    /**
     * 是否启用字段加密（默认 true）
     */
    private boolean enabled = true;

    /**
     * 默认密钥版本
     */
    private int defaultKeyVersion = 1;

    /**
     * 密钥映射（版本号 -> Base64 编码的 32 字节密钥）
     *
     * <p>AES-256 要求密钥长度为 32 字节（256 位）。
     * 生成密钥示例：
     * <pre>{@code
     * KeyGenerator keyGen = KeyGenerator.getInstance("AES");
     * keyGen.init(256);
     * SecretKey key = keyGen.generateKey();
     * String base64Key = Base64.getEncoder().encodeToString(key.getEncoded());
     * }</pre>
     */
    private Map<Integer, String> keys = new LinkedHashMap<>();
}
