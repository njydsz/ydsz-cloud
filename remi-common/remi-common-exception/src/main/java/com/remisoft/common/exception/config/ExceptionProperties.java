package com.remisoft.common.exception.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

/**
 * 异常处理模块配置属性
 *
 * <p>配置前缀：{@code remi.exception}
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * remi:
 *   exception:
 *     metrics-enabled: true
 *     global-handler-enabled: true
 *     response-format: base-response  # 或 problem-detail（RFC 7807）
 *     include-stack-trace: false       # 是否在响应中包含堆栈信息
 *     problem-detail-type-base-url: https://api.example.com/errors
 *     metrics-include-code-tag: false  # 是否在 Micrometer 指标中包含高基数 code tag
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "remi.exception")
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
     * 是否启用错误码文档端点
     */
    private boolean docEndpointEnabled = true;

    /**
     * ProblemDetail type URI 基础 URL（RFC 7807）
     *
     * <p>用于构建 {@code problem.type} 字段，指向错误码文档。
     * 例如：{@code https://api.example.com/errors/BUSINESS_ERROR}
     */
    @NotBlank
    private String problemDetailTypeBaseUrl = "about:blank";

    /**
     * 是否在 Micrometer 指标中包含异常 code tag
     *
     * <p>注意：code tag 为高基数标签，可能导致 Prometheus 指标爆炸。
     * 仅在错误码数量可控且需要按 code 维度查询时开启。
     */
    private boolean metricsIncludeCodeTag = false;

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
