package com.njydsz.common.core.response;

import java.io.Serializable;
import java.net.URI;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.json.annotation.JsonIgnore;
import com.njydsz.common.json.annotation.YdszJsonPropertyOrder;

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
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 构建 ProblemDetail
 * ProblemDetail problem = ProblemDetail.builder()
 *     .type(URI.create("https://errors.ydsz.com/validation"))
 *     .title("参数校验失败")
 *     .status(400)
 *     .detail("字段 'username' 不能为空")
 *     .instance(URI.create("/api/v1/users"))
 *     .build();
 *
 * // 作为 BaseResponse.data 返回
 * return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, (Object) problem);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see BaseResponse#error(ResultCode, Object)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@YdszJsonPropertyOrder({"type", "title", "status", "detail", "instance"})
public class ProblemDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 默认 problem type URI 前缀
     */
    public static final String DEFAULT_TYPE_PREFIX = "https://errors.ydsz.com/";

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
                .build();
    }
}
