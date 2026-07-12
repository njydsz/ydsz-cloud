package com.njydsz.pmis.common.exception;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 7807 Problem Detail 标准错误响应体。
 * <p>
 * 对标 remi-comm ProblemDetail，遵循 RFC 7807 (Problem Details for HTTP APIs)。
 * 生产环境替代简单的 {code, message} 结构，提供更丰富的错误上下文。
 * </p>
 *
 * <pre>
 * {
 *   "type": "https://pmis.njydsz.com/errors/param-error",
 *   "title": "Parameter Validation Error",
 *   "status": 400,
 *   "detail": "Field 'projectName' must not be blank",
 *   "instance": "/api/v1/projects",
 *   "timestamp": "2026-07-12T10:30:00Z",
 *   "errorCode": "A01001",
 *   "errorKey": "param.error",
 *   "traceId": "a1b2c3d4",
 *   "errors": [
 *     {"field": "projectName", "message": "must not be blank"},
 *     {"field": "budget", "message": "must be positive"}
 *   ]
 * }
 * </pre>
 *
 * @author njydsz
 * @since 1.0.0
 */
public class ProblemDetail {

    /** 错误类型 URI（指向错误文档） */
    private URI type;

    /** 错误标题（简短摘要） */
    private String title;

    /** HTTP 状态码 */
    private int status;

    /** 错误详情（人类可读） */
    private String detail;

    /** 请求实例 URI */
    private URI instance;

    /** 时间戳 */
    private Instant timestamp;

    /** PMIS 错误码 */
    private String errorCode;

    /** PMIS 错误 key（用于 i18n） */
    private String errorKey;

    /** 链路追踪 ID */
    private String traceId;

    /** 扩展属性 */
    private Map<String, Object> properties;

    public ProblemDetail() {
        this.timestamp = Instant.now();
    }

    public ProblemDetail(int status, String title, String detail) {
        this();
        this.status = status;
        this.title = title;
        this.detail = detail;
        this.type = URI.create("about:blank");
    }

    public static ProblemDetail of(int status, String title, String detail) {
        return new ProblemDetail(status, title, detail);
    }

    public ProblemDetail type(URI type) {
        this.type = type;
        return this;
    }

    public ProblemDetail instance(URI instance) {
        this.instance = instance;
        return this;
    }

    public ProblemDetail errorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public ProblemDetail errorKey(String errorKey) {
        this.errorKey = errorKey;
        return this;
    }

    public ProblemDetail traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public ProblemDetail property(String key, Object value) {
        if (this.properties == null) {
            this.properties = new LinkedHashMap<>();
        }
        this.properties.put(key, value);
        return this;
    }

    // --- Getters ---

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

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public String getTraceId() {
        return traceId;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setType(URI type) {
        this.type = type;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public void setInstance(URI instance) {
        this.instance = instance;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorKey(String errorKey) {
        this.errorKey = errorKey;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
