package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.api.BizErrorCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常
 *
 * <p>用于业务逻辑校验失败、违反业务规则等场景。与系统异常区分，便于统一响应处理。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final int code;

    /** 错误提示信息 */
    private final String errorMessage;

    /**
     * 根据业务错误码构造异常
     *
     * @param errorCode 业务错误码
     */
    public BizException(BizErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.errorMessage = errorCode.getMessage();
    }

    /**
     * 根据业务错误码与自定义提示信息构造异常
     *
     * @param errorCode 业务错误码
     * @param message   自定义提示信息
     */
    public BizException(BizErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.errorMessage = message;
    }

    /**
     * 根据状态码与提示信息构造异常
     *
     * @param code    状态码
     * @param message 提示信息
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.errorMessage = message;
    }

    /**
     * 仅指定提示信息构造异常，错误码默认为 INTERNAL_ERROR
     *
     * @param message 提示信息
     */
    public BizException(String message) {
        super(message);
        this.code = BizErrorCode.INTERNAL_ERROR.getCode();
        this.errorMessage = message;
    }
}
