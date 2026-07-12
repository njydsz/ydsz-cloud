package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.exception.code.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异常构建器
 *
 * <p>提供链式 API 构建异常，简化异常创建代码。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class ExceptionBuilder {

    private String code;
    private String message;
    private String key;
    private Object[] params;
    private int httpStatus = 400;
    private ExceptionLevel level = ExceptionLevel.ERROR;
    private ExceptionCategory category = ExceptionCategory.BUSINESS;
    private Throwable cause;
    private final Map<String, Object> data = new LinkedHashMap<>();

    private ExceptionBuilder() {
    }

    /**
     * 创建构建器
     *
     * @return 构建器实例
     */
    public static ExceptionBuilder of() {
        return new ExceptionBuilder();
    }

    /**
     * 创建构建器（基于异常码）
     *
     * @param exceptionCode 异常码
     * @return 构建器实例
     */
    public static ExceptionBuilder of(ExceptionCode exceptionCode) {
        return of()
                .code(exceptionCode.getCode())
                .key(exceptionCode.getKey())
                .httpStatus(exceptionCode.getHttpStatus());
    }

    public ExceptionBuilder code(String code) {
        this.code = code;
        return this;
    }

    public ExceptionBuilder message(String message) {
        this.message = message;
        return this;
    }

    public ExceptionBuilder key(String key) {
        this.key = key;
        return this;
    }

    public ExceptionBuilder params(Object... params) {
        this.params = params;
        return this;
    }

    public ExceptionBuilder httpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
        return this;
    }

    public ExceptionBuilder level(ExceptionLevel level) {
        this.level = level;
        return this;
    }

    public ExceptionBuilder category(ExceptionCategory category) {
        this.category = category;
        return this;
    }

    public ExceptionBuilder cause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    public ExceptionBuilder data(String key, Object value) {
        this.data.put(key, value);
        return this;
    }

    /**
     * 构建为 BizException
     *
     * @return BizException
     */
    public BizException build() {
        BizException ex;
        if (cause != null) {
            ex = new BizException(httpStatus, message != null ? message : cause.getMessage());
        } else if (message != null) {
            ex = new BizException(httpStatus, message);
        } else {
            ex = new BizException(httpStatus, "Unknown error");
        }
        return ex;
    }

    /**
     * 构建并抛出
     */
    public void throwIt() {
        throw build();
    }
}
