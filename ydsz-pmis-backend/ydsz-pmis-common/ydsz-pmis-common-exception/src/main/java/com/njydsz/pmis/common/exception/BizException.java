package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.core.response.ResultCode;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.code.ExceptionCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常
 *
 * <p>用于业务逻辑校验失败、违反业务规则等场景。与系统异常区分，便于统一响应处理。
 *
 * <p>i18n 占位符支持：当 {@code errorMessage} 是 i18n key（以 "error." 开头）时，
 * {@code args} 会被透传给 {@link org.springframework.context.MessageSource}，
 * 与 properties 文件中的 {@code {0} {1} ...} 占位符配合实现参数化消息。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务错误码（字符串类型，如 "A10001"） */
    private final String code;

    /** 错误提示信息（或 i18n key，由 GlobalExceptionHandler 决定如何解析） */
    private final String errorMessage;

    /** i18n 占位符参数（与 properties 中的 {0} {1} ... 对应），无参数时为 null */
    private final transient Object[] args;

    /**
     * 根据结果码构造异常
     *
     * @param resultCode 结果码
     */
    public BizException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
        this.errorMessage = resultCode.getMsg();
        this.args = null;
    }

    /**
     * 根据结果码与自定义提示信息构造异常
     *
     * <p>当 {@code message} 以 "error." 开头时，会被视为 i18n key，
     * 由 GlobalExceptionHandler 通过 MessageSource 解析；否则作为最终消息直接展示。
     *
     * @param resultCode 结果码
     * @param message    自定义提示信息（或 i18n key）
     */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.errorMessage = message;
        this.args = null;
    }

    /**
     * 根据结果码与 i18n key + 占位符参数构造异常
     *
     * <p>用法：{@code throw new BizException(StandardResultCode.NOT_FOUND, "error.xxx.msg_yyy", id);}
     * 对应 properties 中：{@code error.xxx.msg_yyy=资源不存在: {0}}
     *
     * <p>注意：本构造器使用 varargs，仅在传入 3 个及以上参数时被 Java 重载解析选中；
     * 仅传 2 个参数时会优先匹配 {@link #BizException(ResultCode, String)}。
     *
     * @param resultCode  结果码
     * @param messageKey  i18n 消息 key（应以 "error." 开头）
     * @param args        占位符参数，与 properties 中的 {0} {1} ... 一一对应
     */
    public BizException(ResultCode resultCode, String messageKey, Object... args) {
        super(messageKey);
        this.code = resultCode.getCode();
        this.errorMessage = messageKey;
        this.args = args;
    }

    /**
     * 根据状态码与提示信息构造异常
     *
     * @param code    状态码（字符串类型）
     * @param message 提示信息
     */
    public BizException(String code, String message) {
        super(message);
        this.code = code;
        this.errorMessage = message;
        this.args = null;
    }

    /**
     * 仅指定提示信息构造异常，错误码默认为 INTERNAL_ERROR
     *
     * @param message 提示信息
     */
    public BizException(String message) {
        super(message);
        this.code = StandardResultCode.INTERNAL_ERROR.getCode();
        this.errorMessage = message;
        this.args = null;
    }

    /**
     * 根据提示信息与原始异常构造业务异常
     *
     * @param message 提示信息
     * @param cause   原始异常
     */
    public BizException(String message, Throwable cause) {
        super(message, cause);
        this.code = StandardResultCode.INTERNAL_ERROR.getCode();
        this.errorMessage = message;
        this.args = null;
    }

    /**
     * 根据 ExceptionCode 构造异常
     *
     * @param code 异常码
     */
    public BizException(ExceptionCode code) {
        super(code.getKey());
        this.code = code.getCode();
        this.errorMessage = code.getKey();
        this.args = null;
    }

    /**
     * 根据 ExceptionCode 构造异常
     *
     * @param code    异常码
     * @param message 提示信息
     */
    public BizException(ExceptionCode code, String message) {
        super(message);
        this.code = code.getCode();
        this.errorMessage = message;
        this.args = null;
    }

    /**
     * 根据 ExceptionCode 构造异常（带 cause）
     *
     * @param code    异常码
     * @param message 提示信息
     * @param cause   原始异常
     */
    public BizException(ExceptionCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code.getCode();
        this.errorMessage = message;
        this.args = null;
    }
}
