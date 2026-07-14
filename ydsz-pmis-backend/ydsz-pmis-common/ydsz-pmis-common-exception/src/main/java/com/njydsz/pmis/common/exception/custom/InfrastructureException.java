package com.njydsz.pmis.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 基础设施异常类
 *
 * <p>用于封装基础设施层故障类异常，如网络连接失败、缓存服务异常、消息队列故障、
 * 文件存储异常等。默认 HTTP 状态码为 500，异常分类为 INFRASTRUCTURE。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * throw new InfrastructureException(UnifiedExceptionCode.NETWORK_ERROR);
 * throw new InfrastructureException("network.error");
 * throw new InfrastructureException(UnifiedExceptionCode.CACHE_ERROR, cause);
 * throw InfrastructureException.of(UnifiedExceptionCode.STORAGE_ERROR).cause(cause).build();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 3.0.0
 * @see UnifiedExceptionCode
 * @see ExceptionCategory#INFRASTRUCTURE
 */
@ToString(callSuper = true)
public class InfrastructureException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    public InfrastructureException() {
        super();
        initDefaults(HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public InfrastructureException(String key) {
        super();
        init(UnifiedExceptionCode.INTERNAL_ERROR.getCode(), key, new Object[]{}, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public InfrastructureException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public InfrastructureException(String key, Object[] params) {
        super();
        init(UnifiedExceptionCode.INTERNAL_ERROR.getCode(), key, params, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public InfrastructureException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public InfrastructureException(String code, String key) {
        super();
        init(code, key, new Object[]{}, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public InfrastructureException(String code, String key, Object[] params) {
        super();
        init(code, key, params, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public InfrastructureException(Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
        this.code = UnifiedExceptionCode.INTERNAL_ERROR.getCode();
    }

    public InfrastructureException(String code, Throwable cause) {
        super(cause);
        initDefaults(HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
        this.code = code;
    }

    public InfrastructureException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        init(exceptionCode, new Object[]{}, ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public InfrastructureException(String code, String key, Throwable cause) {
        super(null, cause);
        init(code, key, new Object[]{}, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public InfrastructureException(String code, String key, Object[] params, Throwable cause) {
        super(null, cause);
        init(code, key, params, HttpStatus.INTERNAL_SERVER_ERROR.value(), ExceptionLevel.ERROR, ExceptionCategory.INFRASTRUCTURE);
    }

    public ExceptionInfo toExceptionInfo() {
        return buildExceptionInfo();
    }

    public static InfrastructureExceptionBuilder builder() {
        return new InfrastructureExceptionBuilder();
    }

    public static InfrastructureException of(String key) {
        return new InfrastructureException(key);
    }

    /**
     * 根据异常码创建基础设施异常
     *
     * @param exceptionCode 异常码枚举
     * @return 基础设施异常实例
     */
    public static InfrastructureException of(ExceptionCode exceptionCode) {
        return new InfrastructureException(exceptionCode);
    }

    public static InfrastructureException of(String code, String key) {
        return new InfrastructureException(code, key);
    }

    public static class InfrastructureExceptionBuilder extends YdszExceptionBuilder<InfrastructureException, InfrastructureExceptionBuilder> {

        @Override
        protected InfrastructureExceptionBuilder self() {
            return this;
        }

        public InfrastructureExceptionBuilder() {
            super();
            this.code = UnifiedExceptionCode.INTERNAL_ERROR.getCode();
            this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR.value();
            this.level = ExceptionLevel.ERROR;
            this.category = ExceptionCategory.INFRASTRUCTURE;
        }

        @Override
        protected InfrastructureException doBuild(String code, String key, Object[] params, int httpStatus,
                                                  ExceptionLevel level, ExceptionCategory category,
                                                  Throwable cause, String path, Object extData, String message) {
            InfrastructureException exception;
            if (cause != null) {
                exception = new InfrastructureException(code, key, params, cause);
            } else {
                exception = new InfrastructureException(code, key, params);
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
