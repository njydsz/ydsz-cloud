package com.njydsz.common.util.security.crypto;

import org.hibernate.validator.constraints.Length;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 加密算法配置属性。
 *
 * <p>配置前缀：{@code ydsz.util.crypto}
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * ydsz:
 *   util:
 *     crypto:
 *       default-algorithm: AES-256-GCM   # 可选：AES-128-GCM / AES-192-GCM / AES-256-GCM / SM4-GCM
 * }</pre>
 *
 * <p>所有可用算法参见 {@link CryptoProviderRegistry#availableAlgorithms()}。
 * 不配置时默认使用 {@code AES-256-GCM}（JDK 自带，无需额外依赖）。
 *
 * @author ydsz-team
 * @since 4.2.0
 * @see CryptoProviderRegistry
 * @see CryptoUtils
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "ydsz.util.crypto")
public class CryptoProperties {

    /**
     * 默认加密算法标识。
     *
     * <p>可选值：{@code AES-128-GCM}、{@code AES-192-GCM}、{@code AES-256-GCM}、{@code SM4-GCM}。
     * 不设置时默认 {@code AES-256-GCM}。
     */
    @Length(min = 1, max = 32)
    private String defaultAlgorithm = "AES-256-GCM";
}
