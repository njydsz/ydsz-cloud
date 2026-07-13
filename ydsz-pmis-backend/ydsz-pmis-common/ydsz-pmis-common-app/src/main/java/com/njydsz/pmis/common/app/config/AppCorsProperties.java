package com.njydsz.pmis.common.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.pmis.common.base.config.BaseCorsProperties;

/**
 * App 端 CORS 跨域配置属性
 *
 * <p>与 Web 端共用 {@link BaseCorsProperties} 的 CORS 字段定义（允许来源、允许方法、允许头、
 * 凭证等），通过不同的配置前缀 {@code ydsz.app.cors} 进行隔离。
 *
 * <p><b>线程安全性：</b>由 Spring Boot 配置属性绑定机制管理，绑定完成后通常视为只读。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ydsz.app.cors")
public class AppCorsProperties extends BaseCorsProperties {
}
