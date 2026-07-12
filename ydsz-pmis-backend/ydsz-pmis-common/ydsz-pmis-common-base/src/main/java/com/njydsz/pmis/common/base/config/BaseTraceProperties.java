package com.njydsz.pmis.common.base.config;

import lombok.Data;

/**
 * 请求追踪/日志配置属性（Web/App 共享基类）
 *
 * <p>子类通过 {@code @ConfigurationProperties} 的 prefix 属性指定具体前缀。
 * 提供链路追踪、请求日志、采样率、响应头等通用配置项。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   web:
 *     trace:
 *       enabled: true
 *       request-log-enabled: true
 *       log-level: INFO
 *       sampling-rate: 1.0
 *       log-request-body: false
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
@Data
public abstract class BaseTraceProperties {

    /**
     * 是否启用链路追踪
     *
     * <p>控制 TraceFilter 是否生效。默认 true。
     */
    private boolean enabled = true;

    /**
     * 是否在响应头中输出追踪/请求 ID
     *
     * <p>启用后会在每个 HTTP 响应头中添加 X-Request-Id。
     * 默认 true，便于客户端关联日志。
     */
    private boolean responseHeaderEnabled = true;

    /**
     * 请求 ID 响应头名称
     *
     * <p>默认值为 "X-Request-Id"，与主流网关、Nginx 等保持一致。
     */
    private String requestIdHeaderName = "X-Request-Id";

    /**
     * 是否启用请求日志记录
     *
     * <p>控制 BaseRequestLogInterceptor 是否记录请求/响应日志。
     * 默认 true，生产环境可根据需要关闭以减少日志量。
     */
    private boolean requestLogEnabled = true;

    /**
     * 请求日志格式
     *
     * <p>支持 "simple" 或 "detailed"。
     * 默认为 "detailed"，包含 IP、UA、请求参数等详细信息。
     */
    private String requestLogFormat = "detailed";

    /**
     * 是否记录请求参数
     *
     * <p>默认 true，会输出 queryString 和 form 参数。
     * 注意：敏感参数（如密码、Token）需自行脱敏。
     */
    private boolean logRequestParams = true;

    /**
     * 是否记录请求体
     *
     * <p>默认 false。开启后会记录完整请求体，可能影响性能并带来敏感信息泄露风险。
     */
    private boolean logRequestBody = false;

    /**
     * 是否记录响应体
     *
     * <p>默认 false。开启后可能产生大量日志。
     */
    private boolean logResponseBody = false;

    /**
     * 请求日志级别
     *
     * <p>支持 "INFO" 或 "DEBUG"。默认为 "INFO"。
     */
    private String logLevel = "INFO";

    /**
     * 日志采样率
     *
     * <p>取值范围 [0, 1]，1.0 表示全量记录，0.0 表示不记录。
     * 用于在高并发场景下减少日志输出量。
     */
    private double samplingRate = 1.0;
}
