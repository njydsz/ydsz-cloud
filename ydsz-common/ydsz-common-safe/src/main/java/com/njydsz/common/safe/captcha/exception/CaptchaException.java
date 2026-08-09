package com.njydsz.common.safe.captcha.exception;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 验证码异常
 *
 * <p>当验证码生成、存储、验证等操作失败时抛出此异常。
 * 异常中可携带验证码 ID，用于问题排查和日志追踪。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CaptchaException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 验证码 ID
     */
    private final String captchaId;

    public CaptchaException(String message) {
        super(UnifiedExceptionCode.FAIL);
        setMessage(message);
        this.captchaId = null;
    }

    public CaptchaException(String message, String captchaId) {
        super(UnifiedExceptionCode.FAIL);
        setMessage(message);
        this.captchaId = captchaId;
    }

    public CaptchaException(String message, Throwable cause) {
        super(UnifiedExceptionCode.FAIL, cause);
        setMessage(message);
        this.captchaId = null;
    }

    public CaptchaException(String message, String captchaId, Throwable cause) {
        super(UnifiedExceptionCode.FAIL, cause);
        setMessage(message);
        this.captchaId = captchaId;
    }

    public String getCaptchaId() {
        return captchaId;
    }
}
