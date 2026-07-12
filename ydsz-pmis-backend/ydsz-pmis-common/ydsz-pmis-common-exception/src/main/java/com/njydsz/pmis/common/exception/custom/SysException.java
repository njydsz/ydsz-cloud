package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;
import lombok.ToString;

/**
 * 系统异常类
 *
 * <p>用于封装基础设施故障类异常，如数据库连接失败、缓存服务异常、消息队列故障等。
 * 默认 HTTP 状态码为 500，异常分类为 SYSTEM。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new SysException(UnifiedExceptionCode.DATABASE_ERROR);
 * throw new SysException("database.error");
 * throw new SysException(UnifiedExceptionCode.CACHE_ERROR, cause);
 * throw SysException.of(UnifiedExceptionCode.INTERNAL_ERROR).cause(cause).build();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see com.njydsz.pmis.common.exception.code.UnifiedExceptionCode
 * @see ExceptionCategory#SYSTEM
 */
@ToString(callSuper = true)
public class SysException extends AbstractRemiException {

    private static final long serialVersionUID = 1L;

    public SysException() {
        super();
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
    }

    public SysException(String key) {
        super();
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = UnifiedExceptionCode.INTERNAL_ERROR.getCode();
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public SysException(ExceptionCode exceptionCode) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public SysException(String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = UnifiedExceptionCode.INTERNAL_ERROR.getCode();
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public SysException(ExceptionCode exceptionCode, Object[] params) {
        super();
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public SysException(String code, String key) {
        super();
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public SysException(String code, String key, Object[] params) {
        super();
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public SysException(Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = UnifiedExceptionCode.INTERNAL_ERROR.getCode();
    }

    public SysException(String code, Throwable cause) {
        super(cause);
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = code;
    }

    public SysException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        this.httpStatus = exceptionCode.getHttpStatus();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = exceptionCode.getCode();
        this.key = exceptionCode.getKey();
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = exceptionCode.getKey();
        this.messageParams = this.params;
    }

    public SysException(String code, String key, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(new Object[]{});
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public SysException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
        this.level = ExceptionLevel.ERROR;
        this.category = ExceptionCategory.SYSTEM;
        this.code = code;
        this.key = key;
        this.params = normalizeParams(params);
        this.message = null;
        this.messageKey = key;
        this.messageParams = this.params;
    }

    public ExceptionInfo toExceptionInfo() {
        return buildExceptionInfo();
    }

    public static SysExceptionBuilder builder() {
        return new SysExceptionBuilder();
    }

    public static SysException of(String key) {
        return new SysException(key);
    }

    public static SysException of(ExceptionCode exceptionCode) {
        return new SysException(exceptionCode);
    }

    public static SysException of(String code, String key) {
        return new SysException(code, key);
    }

    /**
     * 系统异常构建器，预置默认的错误码、HTTP状态码、级别和分类
     */
    public static class SysExceptionBuilder extends RemiExceptionBuilder<SysException, SysExceptionBuilder> {

        public SysExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.INTERNAL_ERROR.getCode();
            this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
            this.level = ExceptionLevel.ERROR;
            this.category = ExceptionCategory.SYSTEM;
        }

        @Override
        protected SysException doBuild(String code, String key, Object[] params, int httpStatus,
                                       ExceptionLevel level, ExceptionCategory category,
                                       Throwable cause, String path, Object extData, String message) {
            SysException exception;
            if (cause != null) {
                exception = new SysException(code, key, params, cause);
            } else {
                exception = new SysException(code, key, params);
            }
            exception.setHttpStatus(httpStatus);
            exception.setLevel(level);
            exception.setCategory(category);
            exception.setPath(path);
            exception.setExtData(extData);
            return exception;
        }
    }
}
