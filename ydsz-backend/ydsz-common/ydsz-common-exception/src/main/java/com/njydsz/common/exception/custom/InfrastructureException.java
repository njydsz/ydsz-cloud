package com.njydsz.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 基础设施异常
 *
 * <p>用于封装基础设施层面的异常（如消息队列、数据库连接、缓存等）。
 *
 * <p><b>默认值：</b>
 * <ul>
 *   <li>HTTP 状态码：500 Internal Server Error</li>
 *   <li>异常级别：ERROR</li>
 *   <li>异常分类：INFRASTRUCTURE</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ToString(callSuper = true)
public class InfrastructureException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_HTTP_STATUS = HttpStatus.INTERNAL_SERVER_ERROR.value();
    private static final ExceptionLevel DEFAULT_LEVEL = ExceptionLevel.ERROR;
    private static final ExceptionCategory DEFAULT_CATEGORY = ExceptionCategory.INFRASTRUCTURE;
    private static final String DEFAULT_CODE = UnifiedExceptionCode.INTERNAL_ERROR.getCode();

    public InfrastructureException() {
        super();
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public InfrastructureException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public InfrastructureException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public InfrastructureException(ExceptionCode exceptionCode, String message) {
        super(message);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        initFields(exceptionCode.getCode(), exceptionCode.getKey(), new Object[]{});
        this.message = message;
        this.messageResolved = true;
    }

    public InfrastructureException(String key) {
        super();
        init(DEFAULT_CODE, key, new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public InfrastructureException(String key, Object[] params) {
        super();
        init(DEFAULT_CODE, key, params, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public InfrastructureException(String code, String key) {
        super();
        init(code, key, new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public InfrastructureException(String code, String key, Object[] params) {
        super();
        init(code, key, params, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public InfrastructureException(Throwable cause) {
        super(cause);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        this.code = DEFAULT_CODE;
    }

    public InfrastructureException(ExceptionCode exceptionCode, Throwable cause) {
        super(null, cause);
        init(exceptionCode, new Object[]{}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public InfrastructureException(String code, String key, Throwable cause) {
        super(null, cause);
        init(code, key, new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }
}
