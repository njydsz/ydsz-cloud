package com.njydsz.common.excel.exception;

/**
 * Excel 模块基础异常类
 *
 * <p>继承 RuntimeException，绑定 {@link ExcelExceptionCode} 异常码枚举。
 * 自包含的异常体系，不依赖全局异常处理模块。</p>
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
 * <h3>全局异常体系桥接</h3>
 * <p>本异常类自包含定义，不直接依赖全局异常体系。
 * 如需接入全局异常处理器，可在调用方通过 instanceof 判断并适配：</p>
 * <pre>{@code
 * // 全局异常处理器中适配示例
 * if (e instanceof ExcelException) {
 *     ExcelExceptionCode code = ((ExcelException) e).getExceptionCode();
 *     // 转换为全局响应...
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExcelReadException
 * @see ExcelWriteException
 * @see ExcelExceptionCode
 */
public class ExcelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码枚举 */
    private final ExcelExceptionCode exceptionCode;

    /** 国际化消息键（冗余存储，用于 i18n 解析） */
    private final String messageKey;

    /** 错误上下文数据（可选，用于格式化消息） */
    private transient Object[] context;

    /**
     * 构造 Excel 异常（使用通用错误码）。
     */
    public ExcelException() {
        super("Excel处理异常");
        this.exceptionCode = ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
        this.messageKey = exceptionCode.getKey();
    }

    /**
     * 构造带错误信息的 Excel 异常。
     *
     * @param message 错误描述
     */
    public ExcelException(String message) {
        super(message);
        this.exceptionCode = ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
        this.messageKey = exceptionCode.getKey();
    }

    /**
     * 构造带错误信息和原因的 Excel 异常。
     *
     * @param message 错误描述
     * @param cause 原始异常
     */
    public ExcelException(String message, Throwable cause) {
        super(message, cause);
        this.exceptionCode = ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
        this.messageKey = exceptionCode.getKey();
    }

    /**
     * 构造带异常码的 Excel 异常。
     *
     * @param exceptionCode 异常码枚举
     */
    public ExcelException(ExcelExceptionCode exceptionCode) {
        super(exceptionCode != null ? exceptionCode.getKey() : "Excel处理异常");
        this.exceptionCode = exceptionCode != null
            ? exceptionCode : ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
        this.messageKey = this.exceptionCode.getKey();
    }

    /**
     * 构造带异常码和自定义消息的 Excel 异常。
     *
     * @param exceptionCode 异常码枚举
     * @param message 自定义错误描述
     */
    public ExcelException(ExcelExceptionCode exceptionCode, String message) {
        super(message);
        this.exceptionCode = exceptionCode != null
            ? exceptionCode : ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
        this.messageKey = this.exceptionCode.getKey();
    }

    /**
     * 构造带异常码、自定义消息和原因的 Excel 异常。
     *
     * @param exceptionCode 异常码枚举
     * @param message 自定义错误描述
     * @param cause 原始异常
     */
    public ExcelException(ExcelExceptionCode exceptionCode, String message, Throwable cause) {
        super(message, cause);
        this.exceptionCode = exceptionCode != null
            ? exceptionCode : ExcelExceptionCode.CONFIG_INVALID_PARAMETER;
        this.messageKey = this.exceptionCode.getKey();
    }

    /**
     * 获取异常码枚举。
     *
     * @return ExcelExceptionCode 枚举值
     */
    public ExcelExceptionCode getExceptionCode() {
        return exceptionCode;
    }

    /**
     * 获取异常码字符串。
     *
     * @return 异常码
     */
    public String getCode() {
        return exceptionCode != null ? exceptionCode.getCode() : null;
    }

    /**
     * 获取 HTTP 状态码。
     *
     * @return HTTP 状态码，默认为 400
     */
    public int getHttpStatus() {
        return exceptionCode != null ? exceptionCode.getHttpStatus() : 400;
    }

    /**
     * 获取国际化消息键。
     *
     * @return 消息键
     */
    public String getMessageKey() {
        return messageKey;
    }

    /**
     * 获取错误上下文数据。
     *
     * @return 上下文数据数组
     */
    public Object[] getContext() {
        return context;
    }

    /**
     * 设置错误上下文数据。
     *
     * @param context 上下文数据数组
     */
    public void setContext(Object[] context) {
        this.context = context;
    }

    /**
     * 获取格式化的错误消息（使用上下文数据格式化）。
     *
     * @return 格式化后的错误消息
     */
    public String getFormattedMessage() {
        String msg = getMessage();
        if (context == null || context.length == 0 || msg == null) {
            return msg;
        }
        try {
            return String.format(msg, context);
        } catch (Exception e) {
            return msg;
        }
    }
}
