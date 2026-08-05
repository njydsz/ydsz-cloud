package com.remisoft.common.core.context;

import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

import com.remisoft.common.core.response.BaseResponse;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import com.remisoft.common.core.code.ResultCode;
import com.remisoft.common.json.annotation.JsonInclude;
import com.remisoft.common.json.annotation.JsonPropertyOrder;

/**
 * RFC 7807 Problem Details 标准错误详情载体
 *
 * <p>实现 <a href="https://datatracker.ietf.org/doc/html/rfc7807">RFC 7807</a>
 * "Problem Details for HTTP APIs" 规范，提供机器可读的错误详情格式。
 *
 * <p>可作为 {@link BaseResponse#getData()} 的内容，在 HTTP API 响应中
 * 携带结构化的错误信息，便于前端和第三方系统统一处理。
 *
 * <p><b>字段映射：</b>
 * <ul>
 *   <li>{@code type} — 问题类型的 URI（通常指向错误文档页面）</li>
 *   <li>{@code title} — 问题类型的简短摘要</li>
 *   <li>{@code status} — HTTP 状态码（与 BaseResponse.code 互补）</li>
 *   <li>{@code detail} — 具体错误详情（包含实例特定信息）</li>
 *   <li>{@code instance} — 问题发生的 URI（通常为请求路径）</li>
 *   <li>{@code traceId} — 链路追踪 ID（用于日志关联）</li>
 *   <li>{@code requestId} — 请求 ID</li>
 *   <li>{@code timestamp} — 错误时间戳</li>
 *   <li>{@code errorCode} — 业务错误码</li>
 *   <li>{@code extensions} — 扩展字段（附加信息）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 方式1: 使用 of() 工厂方法
 * ProblemDetail problem = ProblemDetail.of(BaseResultCode.VALIDATION_FAILED, "字段 'username' 不能为空");
 *
 * // 方式2: 通过 BaseResponse.errorWithDetail()
 * return BaseResponse.errorWithDetail(BaseResultCode.NOT_FOUND, "用户不存在, ID: 12345");
 *
 * // 方式3: 使用 Builder
 * ProblemDetail problem = ProblemDetail.builder()
 *     .type(URI.create("https://errors.remi.com/validation"))
 *     .title("参数校验失败")
 *     .status(400)
 *     .detail("字段 'username' 不能为空")
 *     .instance(URI.create("/api/v1/users"))
 *     .traceId("abc-123")
 *     .build();
 * }</pre>
 *
 * @author remi-team
 * @since 1.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "title", "status", "detail", "instance", "errorCode", "traceId", "requestId", "timestamp"})
public class ProblemDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 默认 problem type URI 前缀
     */
    public static final String DEFAULT_TYPE_PREFIX = "https://errors.remi.com/";

    /**
     * 问题类型的 URI
     *
     * <p>指向问题描述文档的 URI，默认使用 {@value #DEFAULT_TYPE_PREFIX} + 错误码。
     */
    private URI type;

    /**
     * 问题类型的简短摘要
     *
     * <p>人类可读的简短描述，不应包含实例特定信息。
     */
    private String title;

    /**
     * HTTP 状态码
     *
     * <p>与问题关联的 HTTP 状态码，与 {@link BaseResponse#getCode()}（业务码）互补。
     */
    private Integer status;

    /**
     * 具体错误详情
     *
     * <p>包含实例特定信息的详细描述。
     */
    private String detail;

    /**
     * 问题发生的 URI
     *
     * <p>通常为请求路径，帮助定位问题发生的位置。
     */
    private URI instance;

    /**
     * 链路追踪 ID
     *
     * <p>用于日志关联和分布式追踪。
     */
    private String traceId;

    /**
     * 请求 ID
     *
     * <p>标识单次请求的唯一 ID。
     */
    private String requestId;

    /**
     * 错误时间戳
     *
     * <p>错误发生的 UTC 时间。
     */
    private Instant timestamp;

    /**
     * 业务错误码
     *
     * <p>业务系统自定义的错误码字符串。
     */
    private String errorCode;

    /**
     * 扩展字段
     *
     * <p>用于携带附加的上下文信息。
     */
    private Map<String, Object> extensions;

    // ==================== 工厂方法 ====================

    /**
     * 快速构建方法 — 使用 ResultCode 构建
     *
     * @param resultCode 结果码
     * @param detail     错误详情
     * @return ProblemDetail 实例
     */
    public static ProblemDetail of(ResultCode resultCode, String detail) {
        return ProblemDetail.builder()
                .type(URI.create(DEFAULT_TYPE_PREFIX + resultCode.getCode()))
                .title(resultCode.getMsg())
                .status(resultCode.getHttpStatusCode())
                .detail(detail)
                .errorCode(resultCode.getCode())
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 快速构建方法 — 使用 ResultCode + 请求路径
     *
     * @param resultCode 结果码
     * @param detail     错误详情
     * @param instance   请求路径 URI
     * @return ProblemDetail 实例
     */
    public static ProblemDetail of(ResultCode resultCode, String detail, URI instance) {
        return ProblemDetail.builder()
                .type(URI.create(DEFAULT_TYPE_PREFIX + resultCode.getCode()))
                .title(resultCode.getMsg())
                .status(resultCode.getHttpStatusCode())
                .detail(detail)
                .instance(instance)
                .errorCode(resultCode.getCode())
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 快速构建方法 — 使用基本字段构建
     *
     * @param type   错误类型 URI 字符串
     * @param title  错误标题
     * @param status HTTP 状态码
     * @param detail 错误详情
     * @return ProblemDetail 实例
     * @deprecated 自 2.1.0 起废弃，使用 {@link #builder()} Builder 替代
     * @since 1.1.0
     */
    @Deprecated
    public static ProblemDetail of(String type, String title, int status, String detail) {
        return ProblemDetail.builder()
                .type(type != null ? URI.create(type) : null)
                .title(title)
                .status(status)
                .detail(detail)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 快速构建方法 — 使用 URI 类型构建
     *
     * @param type   错误类型 URI
     * @param title  错误标题
     * @param status HTTP 状态码
     * @param detail 错误详情
     * @return ProblemDetail 实例
     * @deprecated 自 2.1.0 起废弃，使用 {@link #builder()} Builder 替代
     * @since 1.1.0
     */
    @Deprecated
    public static ProblemDetail of(URI type, String title, int status, String detail) {
        return ProblemDetail.builder()
                .type(type)
                .title(title)
                .status(status)
                .detail(detail)
                .timestamp(Instant.now())
                .build();
    }
}
