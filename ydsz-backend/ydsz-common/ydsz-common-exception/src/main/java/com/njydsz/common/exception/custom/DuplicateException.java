package com.njydsz.common.exception.custom;

import org.springframework.http.HttpStatus;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;

import lombok.ToString;

/**
 * 重复提交异常
 *
 * <p>用于封装重复提交场景的异常。
 *
 * <p><b>默认值：</b>
 * <ul>
 *   <li>HTTP 状态码：409 Conflict</li>
 *   <li>异常级别：WARN</li>
 *   <li>异常分类：BUSINESS</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ToString(callSuper = true)
public class DuplicateException extends AbstractYdszException {

    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_HTTP_STATUS = HttpStatus.CONFLICT.value();
    private static final ExceptionLevel DEFAULT_LEVEL = ExceptionLevel.WARN;
    private static final ExceptionCategory DEFAULT_CATEGORY = ExceptionCategory.BUSINESS;
    private static final String DEFAULT_CODE = UnifiedExceptionCode.FAIL.getCode();

    public DuplicateException() {
        super();
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public DuplicateException(ExceptionCode exceptionCode) {
        super();
        init(exceptionCode, new Object[]{}, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public DuplicateException(ExceptionCode exceptionCode, Object[] params) {
        super();
        init(exceptionCode, params, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public DuplicateException(String key) {
        super();
        init(DEFAULT_CODE, key, new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public DuplicateException(String key, Object[] params) {
        super();
        init(DEFAULT_CODE, key, params, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public DuplicateException(String code, String key) {
        super();
        init(code, key, new Object[]{}, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public DuplicateException(String code, String key, Object[] params) {
        super();
        init(code, key, params, DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
    }

    public DuplicateException(Throwable cause) {
        super(cause);
        initDefaults(DEFAULT_HTTP_STATUS, DEFAULT_LEVEL, DEFAULT_CATEGORY);
        this.code = DEFAULT_CODE;
    }
}
