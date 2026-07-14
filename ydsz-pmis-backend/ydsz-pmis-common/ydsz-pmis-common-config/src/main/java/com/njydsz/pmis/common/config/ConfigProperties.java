package com.njydsz.pmis.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置加密属性
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "pmis.config.encrypt")
public class ConfigProperties {

    /** 是否启用配置加密 */
    private boolean enabled = true;

    /** 加密密钥（建议通过环境变量 PMIS_CONFIG_ENCRYPT_KEY 设置） */
    private String secretKey;

    /** 密钥环境变量名 */
    private String secretKeyEnv = "PMIS_CONFIG_ENCRYPT_KEY";

    /** 默认密钥（仅用于开发环境，生产环境必须通过环境变量设置） */
    private String defaultKey = "pmis-dev-secret-key-change-in-prod";
}
