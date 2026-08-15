package com.njydsz.common.util.security.crypto;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 加密算法自动配置——将 {@link CryptoProperties} 桥接进 {@link CryptoUtils}。
 *
 * <p>通过 {@code ydsz.util.crypto.default-algorithm} 属性配置默认加密算法，
 * 避免再依赖系统属性 {@code crypto.algorithm}（两者可并存，系统属性优先级更高）。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * ydsz:
 *   util:
 *     crypto:
 *       default-algorithm: SM4-GCM
 * }</pre>
 *
 * @author ydsz-team
 * @since 4.2.0
 * @see CryptoProperties
 * @see CryptoUtils
 */
@AutoConfiguration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoAutoConfiguration {

    /**
     * 将 {@link CryptoProperties#getDefaultAlgorithm()} 注入到 {@link CryptoUtils}。
     *
     * @param properties 加密算法配置属性
     */
    public CryptoAutoConfiguration(CryptoProperties properties) {
        CryptoUtils.setDefaultAlgorithm(properties.getDefaultAlgorithm());
    }
}
