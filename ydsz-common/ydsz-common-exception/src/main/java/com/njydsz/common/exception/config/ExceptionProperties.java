package com.njydsz.common.exception.config;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

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
 *     response-format: base-response  # 或 problem-detail（RFC 7807）
 *     include-stack-trace: false       # 是否在响应中包含堆栈信息
 *     problem-detail-type-base-url: https://api.example.com/errors
 *     metrics-include-code-tag: false  # 是否在 Micrometer 指标中包含高基数 code tag
 *     doc-endpoint:
 *       enabled: true
 *       filter-modules: []             # 白名单模块，空表示全部
 *       auth-required: false           # 是否需要鉴权
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@Setter
@Validated
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
     * 错误码文档端点安全配置
     */
    private DocEndpointSecurity docEndpoint = new DocEndpointSecurity();

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

    /**
     * 错误码文档端点安全配置
     */
    @Getter
    @Setter
    public static class DocEndpointSecurity {
        /**
         * 端点模块白名单（仅允许查看指定模块的错误码）
         * 空列表表示允许所有模块
         */
        private List<String> filterModules = Collections.emptyList();

        /**
         * 是否需要访问鉴权（开启后需结合 Spring Security）
         */
        private boolean authRequired = false;

        /**
         * 鉴权头名称（当 authRequired=true 时校验）
         */
        private String authHeaderName = "X-Actuator-Auth";

        /**
         * 鉴权 Token（简单 token 鉴权，生产建议使用 Spring Security）
         */
        private String authToken = "";
    }
}
