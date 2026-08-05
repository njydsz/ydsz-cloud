package com.remisoft.common.core.response;

import java.io.Serializable;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RFC 9457 (原 RFC 7807) Problem Detail 响应模型
 *
 * <p>采用 Problem Details for HTTP APIs 标准格式，在错误响应体中提供人类可读与机器可读的诊断信息。
 *
 * <p><b>字段语义（均来自 RFC 9457 §3）：</b>
 * <ul>
 *   <li><b>type</b>：标识问题类型的 URI 引用（可为人可读文档链接）。默认 "about:blank"。</li>
 *   <li><b>title</b>：简短、人类可读的问题摘要（应针对所有同类问题保持一致）。</li>
 *   <li><b>status</b>：HTTP 状态码（来自 HTTP 响应）。</li>
 *   <li><b>detail</b>：针对本次问题发生的人类可读的特定解释。</li>
 *   <li><b>instance</b>：标识问题发生具体位置的 URI 引用（可为相对路径如 "/api/users/123"）。</li>
 * </ul>
 *
 * <p><b>与 {@link BaseResponse} 的关系：</b>
 * <ul>
 *   <li>RFC 9457 是错误响应的独立格式，与 BaseResponse 的 {code, msg, data} 二元格式互不干扰</li>
 *   <li>通过配置开关 {@code remi.core.response.rfc9457.enabled=true} 可激活 RFC 格式</li>
 *   <li>激活后，问题详情会作为 ProblemDetail 返回，不再走 BaseResponse.error()</li>
 * </ul>
 *
 * <p><b>与 Spring Boot 3+ 内置 ProblemDetail 的差异：</b>
 * Spring Boot 的 ProblemDetail 仅被其 ErrorAttributes 消费。此类为 Remi 全栈独享：
 * 支持扩展属性 (extensions)、与 ResultCode 的双向桥接、以及 support Actuator 独享观测。
 *
 * <p><b>启用示例：</b>
 * <pre>{@code
 * remi:
 *   core:
 *     response:
 *       rfc9457:
 *         enabled: true
 *         type-uri-prefix: https://docs.remisoft.com/problems
 * }</pre>
 *
 * @author remi-team
 * @since 1.8.0
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457 - Problem Details for HTTP APIs</a>
 */
public class ProblemDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 默认 type URI（当未显式指定时使用）
     */
    public static final String DEFAULT_TYPE = "about:blank";

    /**
     * 问题类型 URI（应指向可有人类可读文档的 URL）
     */
    private final URI type;

    /**
     * 简短、人类可读的问题摘要（应对所有同类问题保持一致）
     */
    private final String title;

    /**
     * HTTP 状态码
     */
    private final int status;

    /**
     * 针对本次问题发生的人类可读特定解释
     */
    private final String detail;

    /**
     * 标识问题发生具体位置的 URI（可为相对路径）
     */
    private final URI instance;

    /**
     * 应用层错误码（Remi 内部错误码体系，非 RFC 9457 标准字段，作为 extension）
     */
    private final String code;

    /**
     * 国际化消息键（Remi 内部字段，作为 extension，用于客户端二次本地化）
     */
    private final String messageKey;

    /**
     * 问题发生时间戳
     */
    private final LocalDateTime timestamp;

    /**
     * 扩展属性 Map（RFC 9457 §3.2 - 问题类型可附加任意辅助信息）
     */
    private final Map<String, Object> extensions;

    /**
     * 私有构造器，请使用 Builder 或静态工厂
     */
    private ProblemDetail(Builder builder) {
        this.type = builder.type != null ? builder.type : URI.create(DEFAULT_TYPE);
        this.title = builder.title;
        this.status = builder.status;
        this.detail = builder.detail;
        this.instance = builder.instance;
        this.code = builder.code;
        this.messageKey = builder.messageKey;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.extensions = builder.extensions != null
            ? Map.copyOf(builder.extensions)
            : Map.of();
    }

    // -------------------------------------------------------------------------
    // 静态工厂
    // -------------------------------------------------------------------------

    /**
     * 创建指定 HTTP 状态的 ProblemDetail
     *
     * @param status HTTP 状态码
     * @return 新 Builder 实例（便于链式构造）
     */
    public static Builder ofStatus(int status) {
        return new Builder().status(status);
    }

    /**
     * 创建指定 HTTP 状态和 detail 的 ProblemDetail
     *
     * @param status HTTP 状态码
     * @param detail 特定问题解释
     * @return 新 ProblemDetail 实例
     */
    public static ProblemDetail of(int status, String detail) {
        return new Builder().status(status).detail(detail).build();
    }

    /**
     * 从应用错误码枚举桥接
     *
     * @param code    应用错误码（需为 CharSequence）
     * @param typeUri 问题类型 URI 前缀
     * @return ProblemDetail 实例
     */
    public static ProblemDetail fromCode(CharSequence code, String typeUri) {
        Objects.requireNonNull(code, "code must not be null");
        String codeStr = code.toString();
        return new Builder()
            .type(URI.create(typeUri + "/" + codeStr.toLowerCase()))
            .title(codeStr)
            .status(resolveHttpStatus(codeStr))
            .code(codeStr)
            .build();
    }

    // -------------------------------------------------------------------------
    // 简单工厂 - 根据 common HTTP 状态构建
    // -------------------------------------------------------------------------

    public static ProblemDetail badRequest(String detail) {
        return ofStatus(400).title("Bad Request").detail(detail).build();
    }

    public static ProblemDetail unauthorized(String detail) {
        return ofStatus(401).title("Unauthorized").detail(detail).build();
    }

    public static ProblemDetail forbidden(String detail) {
        return ofStatus(403).title("Forbidden").detail(detail).build();
    }

    public static ProblemDetail notFound(String detail) {
        return ofStatus(404).title("Not Found").detail(detail).build();
    }

    public static ProblemDetail tooManyRequests(String detail) {
        return ofStatus(429).title("Too Many Requests").detail(detail).build();
    }

    public static ProblemDetail internalError(String detail) {
        return ofStatus(500).title("Internal Server Error").detail(detail).build();
    }

    public static ProblemDetail serviceUnavailable(String detail) {
        return ofStatus(503).title("Service Temporarily Unavailable").detail(detail).build();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public URI getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public int getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public URI getInstance() {
        return instance;
    }

    public String getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    /**
     * 获取扩展属性值（类型安全）
     *
     * @param key 属性键
     * @param <T> 返回类型
     * @return 属性值，不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key) {
        return (T) extensions.get(key);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * ProblemDetail 构建器
     */
    public static final class Builder {
        private URI type;
        private String title;
        private int status = 500;
        private String detail;
        private URI instance;
        private String code;
        private String messageKey;
        private LocalDateTime timestamp;
        private Map<String, Object> extensions;

        public Builder type(URI type) {
            this.type = type;
            return this;
        }

        public Builder type(String typeUri) {
            this.type = URI.create(typeUri);
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

        public Builder instance(String instanceUri) {
            this.instance = URI.create(instanceUri);
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder messageKey(String messageKey) {
            this.messageKey = messageKey;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder extension(String key, Object value) {
            if (extensions == null) {
                extensions = new LinkedHashMap<>();
            }
            extensions.put(key, value);
            return this;
        }

        public Builder extensions(Map<String, Object> extensions) {
            this.extensions = extensions != null ? new LinkedHashMap<>(extensions) : null;
            return this;
        }

        /**
         * 构建 ProblemDetail 实例
         *
         * @return 新的 ProblemDetail
         */
        public ProblemDetail build() {
            return new ProblemDetail(this);
        }
    }

    // -------------------------------------------------------------------------
    // 内部辅助
    // -------------------------------------------------------------------------

    /**
     * 根据 Remi 错误码推断 HTTP 状态（默认 500）
     *
     * <p>仅处理部分高频错误码，其余返回 500。
     */
    private static int resolveHttpStatus(String code) {
        if (code == null) return 500;
        switch (code) {
            case "BAD_REQUEST":
            case "VALIDATION_FAILED":
            case "MISSING_PARAMETER":
            case "INVALID_RANGE":
            case "PAYLOAD_TOO_LARGE":
                return 400;
            case "UNAUTHORIZED":
            case "TOKEN_EXPIRED":
            case "TOKEN_INVALID":
            case "MFA_REQUIRED":
            case "MFA_INVALID":
                return 401;
            case "FORBIDDEN":
            case "DATA_SCOPE_FORBIDDEN":
            case "ACCOUNT_LOCKED":
                return 403;
            case "NOT_FOUND":
                return 404;
            case "METHOD_NOT_ALLOWED":
                return 405;
            case "UNSUPPORTED_MEDIA_TYPE":
                return 415;
            case "DUPLICATE_KEY":
            case "DB_DUPLICATE_KEY":
            case "RESOURCE_LOCKED":
                return 409;
            case "RATE_LIMIT":
            case "TOO_MANY_REQUESTS":
            case "THIRD_PARTY_RATE_LIMITED":
                return 429;
            case "INTERNAL_ERROR":
            case "UNKNOWN":
            default:
                return 500;
            case "SERVICE_UNAVAILABLE":
            case "SYSTEM_MAINTENANCE":
            case "CIRCUIT_BREAKER_OPEN":
                return 503;
            case "REQUEST_TIMEOUT":
            case "DB_QUERY_TIMEOUT":
            case "THIRD_PARTY_TIMEOUT":
                return 504;
        }
    }
}
