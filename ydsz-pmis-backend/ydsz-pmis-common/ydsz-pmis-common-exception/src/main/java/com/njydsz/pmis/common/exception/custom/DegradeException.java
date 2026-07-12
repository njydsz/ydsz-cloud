package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

/**
 * 服务降级异常
 *
 * <p>当服务触降级策略时抛出，表示返回降级后的结果或拒绝服务。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class DegradeException extends AbstractPmisException {

    public DegradeException(String message) {
        super(message);
        setHttpStatus(429);
        setLevel(ExceptionLevel.WARN);
        setCategory(ExceptionCategory.INFRASTRUCTURE);
    }

    public DegradeException(String message, Throwable cause) {
        super(message, cause);
        setHttpStatus(429);
        setLevel(ExceptionLevel.WARN);
        setCategory(ExceptionCategory.INFRASTRUCTURE);
    }
}
