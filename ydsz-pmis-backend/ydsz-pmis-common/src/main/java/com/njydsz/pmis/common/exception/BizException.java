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

    private final int code;
    private final String errorMessage;

    public BizException(BizErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.errorMessage = errorCode.getMessage();
    }

    public BizException(BizErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.errorMessage = message;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.errorMessage = message;
    }

    public BizException(String message) {
        super(message);
        this.code = BizErrorCode.INTERNAL_ERROR.getCode();
        this.errorMessage = message;
    }
}
