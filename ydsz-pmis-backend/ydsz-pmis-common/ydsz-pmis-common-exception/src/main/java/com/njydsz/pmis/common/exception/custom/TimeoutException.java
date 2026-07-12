package com.njydsz.pmis.common.exception.custom;

import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;

/**
 * 超时异常
 *
 * <p>当操作超时时抛出。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class TimeoutException extends AbstractPmisException {

    public TimeoutException(String operation, long timeoutMs) {
        super("Operation timed out: " + operation + " (timeout: " + timeoutMs + "ms)");
        setHttpStatus(504);
        setLevel(ExceptionLevel.WARN);
        setCategory(ExceptionCategory.INFRASTRUCTURE);
    }

    public TimeoutException(String operation, long timeoutMs, Throwable cause) {
        super("Operation timed out: " + operation + " (timeout: " + timeoutMs + "ms)", cause);
        setHttpStatus(504);
        setLevel(ExceptionLevel.WARN);
        setCategory(ExceptionCategory.INFRASTRUCTURE);
    }
}
