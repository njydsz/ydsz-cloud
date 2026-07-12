package com.njydsz.pmis.common.web.config;

import com.njydsz.pmis.common.base.config.BaseCorsProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web 端 CORS 跨域配置属性
 *
 * <p>继承 {@link BaseCorsProperties}，配置前缀：{@code ydsz.web.cors}
 *
 * <p><b>配置示例（YAML）：</b>
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
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseCorsProperties
 */
@ConfigurationProperties(prefix = "ydsz.web.cors")
public class WebCorsProperties extends BaseCorsProperties {
}
