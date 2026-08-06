package com.remisoft.common.safe.captcha.exception;

import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.exception.enums.ExceptionCategory;
import com.remisoft.common.exception.enums.ExceptionLevel;

/**
 * 验证码异常
 *
 * <p>当验证码生成、存储、验证等操作失败时抛出此异常。
 * 异常中可携带验证码 ID，用于问题排查和日志追踪。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class CaptchaException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 验证码 ID
     */
    private final String captchaId;

    public CaptchaException(String message) {
        super("111111", "111111");
        setMessage(message);
        this.captchaId = null;
    }

    public CaptchaException(String message, String captchaId) {
        super("111111", "111111");
        setMessage(message);
        this.captchaId = captchaId;
    }

    public CaptchaException(String message, Throwable cause) {
        super("111111", "111111", cause);
        setMessage(message);
        this.captchaId = null;
    }

    public CaptchaException(String message, String captchaId, Throwable cause) {
        super("111111", "111111", cause);
        setMessage(message);
        this.captchaId = captchaId;
    }

    public String getCaptchaId() {
        return captchaId;
    }
}
