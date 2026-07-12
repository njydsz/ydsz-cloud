package com.njydsz.pmis.common.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS 跨域配置属性（Web/App 共享基类）
 *
 * <p>子类通过 {@code @ConfigurationProperties} 的 prefix 属性指定具体前缀，
 * 例如 Web 端使用 {@code pmis.web.cors}，App 端使用 {@code pmis.app.cors}。
 *
 * <p>本类为抽象基类，定义 CORS 通用配置项，不直接注册为 Spring Bean。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties
public abstract class BaseCorsProperties {

    /**
     * 是否启用 CORS 跨域支持
     */
    private boolean enabled = true;

    /**
     * 是否允许发送 Cookie 等凭证信息
     */
    private boolean allowCredentials = false;

    /**
     * 允许的跨域来源模式列表
     */
    private List<String> allowedOriginPatterns = new ArrayList<>();

    /**
     * 允许的 HTTP 请求头列表
     */
    private List<String> allowedHeaders = new ArrayList<>(Arrays.asList("*"));

    /**
     * 允许的 HTTP 请求方法列表
     */
    private List<String> allowedMethods = new ArrayList<>(Arrays.asList("*"));

    /**
     * 预检请求（OPTIONS）缓存时间（秒）
     */
    private long maxAge = 3600L;

    /**
     * CORS 配置生效的 URL 路径模式
     */
    private String pathPattern = "/**";

    /**
     * 过滤器注册顺序
     */
    private int order = 0;
}
