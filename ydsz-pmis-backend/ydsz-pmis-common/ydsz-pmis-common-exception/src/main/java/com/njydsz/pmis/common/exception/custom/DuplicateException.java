package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 唯一约束冲突异常类
 *
 * <p>用于封装数据唯一约束冲突、重复提交等场景。
 * 默认 HTTP 状态码为 409（Conflict），异常分类为 DUPLICATE。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new DuplicateException(UnifiedExceptionCode.DATA_ALREADY_EXISTS);
 * throw DuplicateException.of(UnifiedExceptionCode.DUPLICATE_SUBMISSION).build();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 3.0.0
 */
@ToString(callSuper = true)
public class DuplicateException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    public DuplicateException() {
        super();
        this.httpStatus = HttpStatus.CONFLICT.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.DUPLICATE;
    }

    public DuplicateException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.DUPLICATE;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public DuplicateException(String key) {
        super();
        this.httpStatus = HttpStatus.CONFLICT.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.DUPLICATE;
        this.code = UnifiedExceptionCode.FAIL.getCode();
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public DuplicateException(ExceptionCode exceptionCode, Object[] params) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.DUPLICATE;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public DuplicateException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.CONFLICT.value();
        this.level = ExceptionLevel.WARN;
        this.category = ExceptionCategory.DUPLICATE;
        this.code = UnifiedExceptionCode.FAIL.getCode();
    }

    public ExceptionInfo toExceptionInfo() {
        return buildExceptionInfo();
    }

    public static DuplicateException of(ExceptionCode exceptionCode) {
        return new DuplicateException(exceptionCode);
    }

    public static DuplicateExceptionBuilder builder() {
        return new DuplicateExceptionBuilder();
    }

    /**
     * 重复异常构建器
     */
    public static class DuplicateExceptionBuilder extends YdszExceptionBuilder<DuplicateException, DuplicateExceptionBuilder> {

        @Override
        protected DuplicateExceptionBuilder self() {
            return this;
        }

        public DuplicateExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.FAIL.getCode();
            this.httpStatus = HttpStatus.CONFLICT.value();
            this.level = ExceptionLevel.WARN;
            this.category = ExceptionCategory.DUPLICATE;
        }

        @Override
        protected DuplicateException doBuild(String code, String key, Object[] params, int httpStatus,
                                              ExceptionLevel level, ExceptionCategory category,
                                              Throwable cause, String path, Object extData, String message) {
            DuplicateException exception = new DuplicateException();
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
