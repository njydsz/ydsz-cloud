package com.njydsz.common.exception.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 异常处理模块配置属性
 *
 * <p>配置前缀：{@code ydsz.exception}
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   exception:
 *     metrics-enabled: true
 *     global-handler-enabled: true
 *     trace-enabled: true
 *     response-format: base-response  # 或 problem-detail（RFC 7807）
 *     include-stack-trace: false       # 是否在响应中包含堆栈信息
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ydsz.exception")
public class ExceptionProperties {

    /**
     * 是否启用异常指标统计
     */
    private boolean metricsEnabled = true;

    /**
     * 是否启用全局异常处理器
     */
    private boolean globalHandlerEnabled = true;

    /**
     * 是否启用 TraceId 注入过滤器
     */
    private boolean traceEnabled = true;

    /**
     * 响应格式
     *
     * <ul>
     *   <li>{@code base-response} — 返回 {@code BaseResponse} 格式（默认）</li>
     *   <li>{@code problem-detail} — 返回 RFC 7807 ProblemDetail 格式</li>
     * </ul>
     */
    private ResponseFormat responseFormat = ResponseFormat.BASE_RESPONSE;

    /**
     * 是否在响应中包含异常堆栈信息（仅开发/测试环境建议开启）
     */
    private boolean includeStackTrace = false;

    /**
     * 是否启用异常告警
     */
    private boolean alertEnabled = true;

    /**
     * 是否启用错误码文档端点
     */
    private boolean docEndpointEnabled = true;

    /**
     * 告警去重时间窗口（秒）
     */
    private int alertDedupWindowSeconds = 60;

    /**
     * 响应格式枚举
     */
    public enum ResponseFormat {
        /** BaseResponse 格式 */
        BASE_RESPONSE,
        /** RFC 7807 ProblemDetail 格式 */
        PROBLEM_DETAIL
    }
}
