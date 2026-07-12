package com.njydsz.pmis.common.exception;

import java.time.Instant;

/**
 * 异常响应 DTO
 *
 * <p>统一的错误响应格式，用于 API 返回。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class ExceptionResponse {

    private int status;
    private String error;
    private String message;
    private String code;
    private String traceId;
    private String path;
    private Instant timestamp;

    public ExceptionResponse() {
        this.timestamp = Instant.now();
    }

    public ExceptionResponse(int status, String error, String message) {
        this();
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public static ExceptionResponse of(int status, String error, String message) {
        return new ExceptionResponse(status, error, message);
    }

    public ExceptionResponse code(String code) {
        this.code = code;
        return this;
    }

    public ExceptionResponse traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    public ExceptionResponse path(String path) {
        this.path = path;
        return this;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getCode() {
        return code;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getPath() {
        return path;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
