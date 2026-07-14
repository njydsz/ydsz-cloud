package com.njydsz.pmis.common.util.bean;

/**
 * Bean 拷贝异常
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class BeanCopyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BeanCopyException(String message) {
        super(message);
    }

    public BeanCopyException(String message, Throwable cause) {
        super(message, cause);
    }
}
