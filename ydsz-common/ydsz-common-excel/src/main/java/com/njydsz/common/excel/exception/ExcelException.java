package com.njydsz.common.excel.exception;

import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCode;

/**
 * Excel 模块基础异常类
 *
 * <p>继承 {@link BusinessException}，与 common-exception 异常体系无缝集成。
 * 支持 i18n 消息解析、异常分类、HTTP 状态码映射等能力。</p>
 *
 * <h3>异常层次</h3>
 * <ul>
 *   <li>{@link ExcelReadException} - Excel 读取异常</li>
 *   <li>{@link ExcelWriteException} - Excel 写入异常</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 使用异常码枚举
 * throw new ExcelException(ExcelExceptionCode.CONFIG_INVALID_PARAMETER);
 *
 * // 带自定义消息
 * throw new ExcelException(ExcelExceptionCode.READ_IO_ERROR, "文件读取失败: " + filePath);
 *
 * // 带原始异常
 * throw new ExcelException(ExcelExceptionCode.READ_CONVERSION_FAILED, "类型转换失败", e);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExcelReadException
 * @see ExcelWriteException
 * @see ExcelExceptionCode
 */
public class ExcelException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /** 错误上下文数据 */
    private transient Object[] context;

    /**
     * 构造 Excel 异常
     */
    public ExcelException() {
        super();
    }

    /**
     * 构造带错误信息的 Excel 异常
     *
     * @param message 错误描述
     */
    public ExcelException(String message) {
        super();
        setMessage(message);
    }

    /**
     * 构造带错误信息和原因的 Excel 异常
     *
     * @param message 错误描述
     * @param cause 原始异常
     */
    public ExcelException(String message, Throwable cause) {
        super(UnifiedExceptionCode.FAIL, cause);
        setMessage(message);
    }

    /**
     * 构造带异常码的 Excel 异常
     *
     * @param exceptionCode 异常码枚举
     */
    public ExcelException(ExceptionCode exceptionCode) {
        super(exceptionCode);
    }

    /**
     * 构造带异常码和自定义消息的 Excel 异常
     *
     * @param exceptionCode 异常码枚举
     * @param message 自定义错误描述
     */
    public ExcelException(ExceptionCode exceptionCode, String message) {
        super(exceptionCode);
        setMessage(message);
    }

    /**
     * 构造带异常码、自定义消息和原因的 Excel 异常
     *
     * @param exceptionCode 异常码枚举
     * @param message 自定义错误描述
     * @param cause 原始异常
     */
    public ExcelException(ExceptionCode exceptionCode, String message, Throwable cause) {
        super(exceptionCode, cause);
        setCode(exceptionCode.getCode());
        this.messageKey = exceptionCode.getKey();
        setMessage(message);
    }

    /**
     * 获取错误上下文数据
     *
     * @return 上下文数据数组
     */
    public Object[] getContext() {
        return context;
    }

    /**
     * 设置错误上下文数据
     *
     * @param context 上下文数据数组
     */
    public void setContext(Object[] context) {
        this.context = context;
    }

    /**
     * 获取格式化的错误消息
     *
     * @return 格式化后的错误消息
     */
    public String getFormattedMessage() {
        String msg = getMessage();
        if (context == null || context.length == 0) {
            return msg;
        }
        try {
            return String.format(msg, context);
        } catch (Exception e) {
            return msg;
        }
    }
}
