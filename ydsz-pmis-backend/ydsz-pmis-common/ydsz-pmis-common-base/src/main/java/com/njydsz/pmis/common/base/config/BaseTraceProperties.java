package com.njydsz.pmis.common.base.config;

import lombok.Data;

/**
 * 请求追踪/日志配置属性（Web/App 共享基类）
 *
 * <p>子类通过 {@code @ConfigurationProperties} 的 prefix 属性指定具体前缀。
 * 提供链路追踪、请求日志、采样率、响应头等通用配置项。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public abstract class BaseTraceProperties {

    /**
     * 是否启用链路追踪
     */
    private boolean enabled = true;

    /**
     * 是否在响应头中输出追踪请求 ID
     */
    private boolean responseHeaderEnabled = true;

    /**
     * 请求 ID 响应头名称
     */
    private String requestIdHeaderName = "X-Request-Id";

    /**
     * 是否启用请求日志记录
     */
    private boolean requestLogEnabled = true;

    /**
     * 请求日志格式（"simple" 或 "detailed"）
     */
    private String requestLogFormat = "detailed";

    /**
     * 是否记录请求参数
     */
    private boolean logRequestParams = true;

    /**
     * 是否记录请求体
     */
    private boolean logRequestBody = false;

    /**
     * 是否记录响应体
     */
    private boolean logResponseBody = false;

    /**
     * 请求日志级别（"INFO" 或 "DEBUG"）
     */
    private String logLevel = "INFO";

    /**
     * 日志采样率（[0, 1]）
     */
    private double samplingRate = 1.0;
}
