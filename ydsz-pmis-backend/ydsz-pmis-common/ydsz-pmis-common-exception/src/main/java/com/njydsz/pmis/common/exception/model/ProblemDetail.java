package com.njydsz.pmis.common.exception.model;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import com.njydsz.pmis.common.json.annotation.YdszJsonField;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RFC 7807 Problem Details
 *
 * <p>标准化错误响应格式，包含类型、标题、状态码、详情、实例等字段。
 * 兼容 Spring Boot 3.x 内置的 {@code ProblemDetail} 风格（自定义字段集不同）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * ProblemDetail pd = ProblemDetail.builder()
 *     .type(URI.create("about:blank"))
 *     .title("Validation Failed")
 *     .status(400)
 *     .detail("参数校验失败")
 *     .instance(URI.create("/api/v1/users"))
 *     .traceId("abc-123")
 *     .errorCode("A01052")
 *     .timestamp(Instant.now())
 *     .build();
 * }</pre>
 *
 * @see <a href="https://tools.ietf.org/html/rfc7807">RFC 7807</a>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@YdszJsonField(notWriteNullValue = true)
public class ProblemDetail {

    /**
     * 错误类型 URI，用于机器可读（RFC 7807 规范要求 URI 类型）
     */
    private URI type;

    /**
     * 错误标题，简短的人类可读描述
     */
    private String title;

    /**
     * HTTP 状态码
     */
    private int status;

    /**
     * 错误详情，供人类阅读
     */
    private String detail;

    /**
     * 发生错误的 URI，用于标识问题实例（RFC 7807 规范要求 URI 类型）
     */
    private URI instance;

    /**
     * 追踪 ID，用于日志关联
     */
    private String traceId;

    /**
     * 请求 ID
     */
    private String requestId;

    /**
     * 错误时间戳
     */
    private Instant timestamp;

    /**
     * 错误码，业务系统自定义编码
     */
    private String errorCode;

    /**
     * 扩展字段，用于附加信息
     */
    private Map<String, Object> extensions;

    /**
     * 便捷工厂方法：成功
     */
    public static ProblemDetail success() {
        return ProblemDetail.builder()
                .type(URI.create("about:blank"))
                .title("Success")
                .status(200)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 便捷工厂方法：从异常信息构建（type 参数为 URI 字符串，内部转换）
     *
     * @param type   错误类型 URI 字符串
     * @param title  错误标题
     * @param status HTTP 状态码
     * @param detail 错误详情
     * @return ProblemDetail 实例
     */
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
     * 便捷工厂方法：从 URI 类型构建
     *
     * @param type   错误类型 URI
     * @param title  错误标题
     * @param status HTTP 状态码
     * @param detail 错误详情
     * @return ProblemDetail 实例
     */
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
