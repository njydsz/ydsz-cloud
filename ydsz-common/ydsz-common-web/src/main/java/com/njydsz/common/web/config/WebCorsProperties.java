package com.njydsz.common.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.njydsz.common.base.config.BaseCorsProperties;

/**
 * Web 端 CORS 跨域配置属性
 *
 * <p>继承 {@link BaseCorsProperties}，配置前缀：{@code ydsz.web.cors}
 *
 * <p><b>配置示例（YAML）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   web:
 *     cors:
 *       allowed-origins: "http://localhost:3000,https://admin.example.com"
 *       allowed-methods: "GET,POST,PUT,DELETE,OPTIONS"
 *       allowed-headers: "*"
 *       allow-credentials: true
 *       max-age: 3600
 * }</pre>
 *
 * @author ydsz-team
 * @see BaseCorsProperties
 * @since 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.web.cors")
public class WebCorsProperties extends BaseCorsProperties {}
