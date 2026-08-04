package com.remisoft.common.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.remisoft.common.base.config.BaseCorsProperties;

/**
 * Web 端 CORS 跨域配置属性
 *
 * <p>继承 {@link BaseCorsProperties}，配置前缀：{@code remi.web.cors}
 *
 * <p><b>配置示例（YAML）：</b>
 * <pre>{@code
 * remi:
 *   web:
 *     cors:
 *       allowed-origins: "http://localhost:3000,https://admin.example.com"
 *       allowed-methods: "GET,POST,PUT,DELETE,OPTIONS"
 *       allowed-headers: "*"
 *       allow-credentials: true
 *       max-age: 3600
 * }</pre>
 *
 * @author remi-team
 * @see BaseCorsProperties
 * @since 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "remi.web.cors")
public class WebCorsProperties extends BaseCorsProperties {
}
