package com.njydsz.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 并发冲突异常类
 *
 * <p>用于封装乐观锁冲突、并发修改冲突等场景。
 * 默认 HTTP 状态码为 409（Conflict），异常分类为 CONCURRENCY。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new ConcurrencyException(UnifiedExceptionCode.DATA_CONFLICT);
 * throw ConcurrencyException.of(UnifiedExceptionCode.DATA_CONFLICT).build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ToString(callSuper = true)
public class ConcurrencyException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    /**
     * 默认构造函数，初始化为 409 Conflict / WARN / CONCURRENCY
     */
    public ConcurrencyException() {
        super();
        initDefaults(HttpStatus.CONFLICT.value(), ExceptionLevel.WARN, ExceptionCategory.CONCURRENCY);
    }

    /**
     * 使用异常码枚举构造并发冲突异常
     *
     * @param exceptionCode 异常码枚举
     */
    public ConcurrencyException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, ExceptionLevel.WARN, ExceptionCategory.CONCURRENCY);
    }

    /**
     * 使用国际化消息键构造并发冲突异常
     *
     * @param key 国际化消息键
     */
    public ConcurrencyException(String key) {
        super();
        init(UnifiedExceptionCode.FAIL.getCode(), key, new Object[]{}, HttpStatus.CONFLICT.value(), ExceptionLevel.WARN, ExceptionCategory.CONCURRENCY);
    }

    /**
     * 使用异常码枚举和参数构造并发冲突异常
     *
     * @param exceptionCode 异常码枚举
     * @param params        消息参数
     */
    public ConcurrencyException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, ExceptionLevel.WARN, ExceptionCategory.CONCURRENCY);
    }

    /**
     * 使用原始异常构造并发冲突异常
     *
     * @param cause 原始异常
     */
    public ConcurrencyException(Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.CONFLICT.value(), ExceptionLevel.WARN, ExceptionCategory.CONCURRENCY);
        this.code = UnifiedExceptionCode.FAIL.getCode();
    }

    public ExceptionInfo toExceptionInfo() {
        return buildExceptionInfo();
    }

    public static ConcurrencyException of(ExceptionCode exceptionCode) {
        return new ConcurrencyException(exceptionCode);
    }

    public static ConcurrencyExceptionBuilder builder() {
        return new ConcurrencyExceptionBuilder();
    }

    /**
     * 并发冲突异常构建器
     */
    public static class ConcurrencyExceptionBuilder extends YdszExceptionBuilder<ConcurrencyException, ConcurrencyExceptionBuilder> {

        @Override
        protected ConcurrencyExceptionBuilder self() {
            return this;
        }

        public ConcurrencyExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.FAIL.getCode();
            this.httpStatus = HttpStatus.CONFLICT.value();
            this.level = ExceptionLevel.WARN;
            this.category = ExceptionCategory.CONCURRENCY;
        }

        @Override
        protected ConcurrencyException doBuild(String code, String subCode, String key, Object[] params, int httpStatus,
                                                ExceptionLevel level, ExceptionCategory category,
                                                Throwable cause, String path, Object extData, String message) {
            ConcurrencyException exception = new ConcurrencyException();
            exception.initFields(code, key, params);
            exception.setSubCode(subCode);
            exception.setHttpStatus(httpStatus);
            exception.setLevel(level);
            exception.setCategory(category);
            exception.setPath(path);
            exception.setExtData(extData);
            if (cause != null) {
                exception.initCause(cause);
            }
            return exception;
        }
    }
}
