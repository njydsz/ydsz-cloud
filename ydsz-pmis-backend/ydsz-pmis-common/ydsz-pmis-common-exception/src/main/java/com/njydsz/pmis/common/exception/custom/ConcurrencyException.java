package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.0.0
 */
@ToString(callSuper = true)
public class ConcurrencyException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    public ConcurrencyException() {
        super();
        this.httpStatus = HttpStatus.CONFLICT.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.CONCURRENCY;
    }

    public ConcurrencyException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.CONCURRENCY;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public ConcurrencyException(String key) {
        super();
        this.httpStatus = HttpStatus.CONFLICT.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.CONCURRENCY;
        this.code = UnifiedExceptionCode.FAIL.getCode();
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ConcurrencyException(ExceptionCode exceptionCode, Object[] params) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.CONCURRENCY;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public ConcurrencyException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.CONFLICT.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.CONCURRENCY;
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
        protected ConcurrencyException doBuild(String code, String key, Object[] params, int httpStatus,
                                                ExceptionLevel level, ExceptionCategory category,
                                                Throwable cause, String path, Object extData, String message) {
            ConcurrencyException exception = new ConcurrencyException();
            exception.initFields(code, key, params);
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
