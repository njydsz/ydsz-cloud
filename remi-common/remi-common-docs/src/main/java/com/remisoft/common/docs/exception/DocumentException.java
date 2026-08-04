package com.remisoft.common.docs.exception;

import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.exception.enums.ExceptionCode;

/**
 * 文档处理异常
 * <p>
 * 文档解析、预处理、安全扫描、PII 检测、脱敏、水印等操作失败时抛出。
 *
 * @author remi-team
 * @since 1.0.0
 */
public class DocumentException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造文档处理异常
     *
     * @param exceptionCode 异常码
     */
    public DocumentException(ExceptionCode exceptionCode) {
        super(exceptionCode);
    }

    /**
     * 构造文档处理异常（带原因）
     *
     * @param exceptionCode 异常码
     * @param cause         导致此异常的原始原因
     */
    public DocumentException(ExceptionCode exceptionCode, Throwable cause) {
        super(cause);
        this.code = exceptionCode.getCode();
        this.messageKey = exceptionCode.getKey();
    }

    /**
     * 构造文档处理异常（带自定义消息）
     *
     * @param exceptionCode 异常码
     * @param message       自定义异常消息
     */
    public DocumentException(ExceptionCode exceptionCode, String message) {
        super(exceptionCode);
        this.message = message;
    }

    /**
     * 构造文档处理异常（带自定义消息和原因）
     *
     * @param exceptionCode 异常码
     * @param message       自定义异常消息
     * @param cause         导致此异常的原始原因
     */
    public DocumentException(ExceptionCode exceptionCode, String message, Throwable cause) {
        super(cause);
        this.code = exceptionCode.getCode();
        this.messageKey = exceptionCode.getKey();
        this.message = message;
    }
}
