package com.njydsz.pmis.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异常信息持有类
 *
 * <p>封装异常的完整上下文信息，用于日志记录、审计和监控。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class ExceptionInfo {

    private String code;
    private String message;
    private String traceId;
    private String path;
    private String method;
    private Instant timestamp;
    private String exceptionClass;
    private String stackTrace;
    private Map<String, Object> extra = new LinkedHashMap<>();

    public ExceptionInfo() {
        this.timestamp = Instant.now();
    }

    public static ExceptionInfo of(Throwable throwable) {
        ExceptionInfo info = new ExceptionInfo();
        info.exceptionClass = throwable.getClass().getName();
        info.message = throwable.getMessage();
        return info;
    }

    public ExceptionInfo code(String code) {
        this.code = code;
        return this;
    }

    public ExceptionInfo message(String message) {
        this.message = message;
        return this;
    }

    public ExceptionInfo traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public ExceptionInfo path(String path) {
        this.path = path;
        return this;
    }

    public ExceptionInfo method(String method) {
        this.method = method;
        return this;
    }

    public ExceptionInfo exceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
        return this;
    }

    public ExceptionInfo stackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
        return this;
    }

    public ExceptionInfo extra(String key, Object value) {
        this.extra.put(key, value);
        return this;
    }

    // Getters

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }
}
