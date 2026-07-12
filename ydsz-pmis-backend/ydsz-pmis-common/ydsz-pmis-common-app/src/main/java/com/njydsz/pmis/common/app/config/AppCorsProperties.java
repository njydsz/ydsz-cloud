package com.njydsz.pmis.common.app.config;

import com.njydsz.pmis.common.base.config.BaseCorsProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App 端 CORS 跨域配置属性
 *
 * <p>与 Web 端共用 {@link BaseCorsProperties} 的 CORS 字段定义，
 * 通过不同的配置前缀 {@code pmis.app.cors} 进行隔离。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "pmis.app.cors")
public class AppCorsProperties extends BaseCorsProperties {
}
