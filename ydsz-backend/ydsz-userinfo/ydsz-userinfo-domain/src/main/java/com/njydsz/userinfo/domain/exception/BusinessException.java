package com.njydsz.userinfo.domain.exception;

import com.njydsz.common.core.response.ResultCode;

import lombok.Getter;

/**
 * 用户信息中心业务异常。
 *
 * <p>封装 ResultCode 错误码，由全局异常处理器统一转换为 BaseResponse。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
