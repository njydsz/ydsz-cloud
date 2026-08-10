package com.njydsz.common.core.response;

import com.njydsz.common.core.code.ResultCode;
import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonInclude;
import com.njydsz.common.json.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.net.URI;

/**
 * RFC 9457 Problem Details 错误响应载体。
 *
 * <p>遵循 <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457</a> 标准，
 * 提供结构化机器可读的错误描述，供前端/客户端通过 type 字段识别错误类别、
 * 通过 instance 定位具体请求。</p>
 *
 * <p>与 {@link BaseResponse} 的 {@code error(ResultCode)} 路径互补：
 * BaseResponse 承载通用成功信封 + 简单错误码；
 * ProblemDetail 承载符合行业标准的错误详情（type/title/status/detail/instance/extensions）。
 * 两者可由全局 {@code @ControllerAdvice} 分别输出，取决于 {@code Accept: application/problem+json} 协商。</p>
 *
 * <h3>字段语义（RFC 9457 §3）</h3>
 * <ul>
 *   <li><b>type</b>（可选，默认 about:blank）：标识错误类型的 URI，可跳转文档</li>
 *   <li><b>title</b>（可选）：简短的、可读的错误概述</li>
 *   <li><b>status</b>（必选）：HTTP 状态码数值</li>
 *   <li><b>detail</b>（可选）：此特定故障的详细描述</li>
 *   <li><b>instance</b>（可选）：标识具体故障发生的 URI 引用</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 500 错误
 * return ProblemDetail.builder()
 *     .type(URI.create("https://api.example.com/errors/internal"))
 *     .title("Internal Server Error")
 *     .status(500)
 *     .detail("数据库连接异常，请稍后重试")
 *     .instance(URI.create("/api/v1/users"))
 *     .putExtension("traceId", traceId)
 *     .build();
 *
 * // 直接由 ResultCode 创建
 * return ProblemDetail.of(BaseResultCode.VALIDATION_FAILED, "邮箱格式不正确");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.10.0
 *
 * @see BaseResponse
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "title", "status", "detail", "instance"})
@JsonClass(description = "RFC 9457 错误详情载体")
public class ProblemDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 默认 type URI（RFC 9457 §3.1.2: about:blank indicates no type information）。 */
    public static final URI DEFAULT_TYPE = URI.create("about:blank");

    private URI type;
    private String title;
    private int status;
    private String detail;
    private URI instance;

    /** RFC 9457 §3.2: 供实现方添加自定义字段。 */
    private java.util.Map<String, Object> extensions;

    /** 默认构造函数。 */
    public ProblemDetail() {
    }

    private ProblemDetail(Builder builder) {
        this.type = builder.type;
        this.title = builder.title;
        this.status = builder.status;
        this.detail = builder.detail;
        this.instance = builder.instance;
        this.extensions = builder.extensions;
    }

    /**
     * 静态 Builder 创建入口。
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 由 {@link ResultCode} 创建 ProblemDetail（自动映射 code → status + title）。
     *
     * @param resultCode 结果码
     * @return ProblemDetail 实例
     */
    public static ProblemDetail of(ResultCode resultCode) {
        return builder()
                .status(resultCode.getHttpStatus())
                .title(resultCode.getMsg())
                .putExtension("code", resultCode.getCode())
                .build();
    }

    /**
     * 由 {@link ResultCode} + 自定义详情 创建 ProblemDetail。
     *
     * @param resultCode 结果码
     * @param detail     详细描述（可覆盖 resultCode 默认消息）
     * @return ProblemDetail 实例
     */
    public static ProblemDetail of(ResultCode resultCode, String detail) {
        return builder()
                .status(resultCode.getHttpStatus())
                .title(resultCode.getMsg())
                .detail(detail)
                .putExtension("code", resultCode.getCode())
                .build();
    }

    public URI getType() {
        return type != null ? type : DEFAULT_TYPE;
    }

    public void setType(URI type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public URI getInstance() {
        return instance;
    }

    public void setInstance(URI instance) {
        this.instance = instance;
    }

    public java.util.Map<String, Object> getExtensions() {
        return extensions != null
                ? java.util.Collections.unmodifiableMap(extensions)
                : java.util.Collections.emptyMap();
    }

    /**
     * RFC 9457 ProblemDetail Builder。
     */
    public static final class Builder {
        private URI type;
        private String title;
        private int status;
        private String detail;
        private URI instance;
        private java.util.Map<String, Object> extensions;

        Builder() {
        }

        public Builder type(URI type) {
            this.type = type;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }

        public Builder instance(URI instance) {
            this.instance = instance;
            return this;
        }

        public Builder putExtension(String key, Object value) {
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException("extension key must not be null or empty");
            }
            if (this.extensions == null) {
                this.extensions = new java.util.LinkedHashMap<>(4);
            }
            this.extensions.put(key, value);
            return this;
        }

        public ProblemDetail build() {
            return new ProblemDetail(this);
        }
    }
}
