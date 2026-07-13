package com.njydsz.pmis.common.excel.exception;

/**
 * ExcelFacade 基础异常类
 *
 * <p>ExcelFacade 框架所有异常的根类，封装框架运行时的各类错误。
 * 采用分层异常设计，便于调用方针对性处理。</p>
 *
 * <h3>异常层次</h3>
 * <ul>
 *   <li>{@link ExcelReadException} - Excel 读取异常</li>
 *   <li>{@link ExcelWriteException} - Excel 写入异常</li>
 * </ul>
 *
 * <h3>异常处理策略</h3>
 * <ul>
 *   <li>传播模式 - 将底层异常包装后抛出，保留异常链</li>
 *   <li>上下文信息 - 提供详细的错误描述和解决方案提示</li>
 *   <li>日志记录 - 异常包含足够的调试信息</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * try {
 *     ExcelFacade.read("data.xlsx", User.class).doRead(listener);
 * } catch (ExcelReadException e) {
 *     // 针对读取异常进行处理
 *     log.error("Excel读取失败: {}", e.getMessage(), e);
 * } catch (ExcelException e) {
 *     // 其他框架异常
 *     log.error("Excel处理失败", e);
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 * @see ExcelReadException
 * @see ExcelWriteException
 */
public class ExcelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private String errorCode;

    /** 错误上下文数据 */
    private transient Object[] context;

    /**
     * 构造基础异常
     */
    public ExcelException() {
        super();
    }

    /**
     * 构造带错误信息的异常
     *
     * @param message 错误描述
     */
    public ExcelException(String message) {
        super(message);
    }

    /**
     * 构造带错误信息和原因的异常
     *
     * @param message 错误描述
     * @param cause 原始异常
     */
    public ExcelException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造带错误码和错误信息的异常
     *
     * @param errorCode 错误码
     * @param message 错误描述
     */
    public ExcelException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造带错误码、错误信息和原因的异常
     *
     * @param errorCode 错误码
     * @param message 错误描述
     * @param cause 原始异常
     */
    public ExcelException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 设置错误码
     *
     * @param errorCode 错误码
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
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
     * <p>如果设置了上下文数据，使用 MessageFormat 格式化消息</p>
     *
     * @return 格式化后的错误消息
     */
    public String getFormattedMessage() {
        if (context == null || context.length == 0) {
            return getMessage();
        }
        try {
            return String.format(getMessage(), context);
        } catch (Exception e) {
            return getMessage();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ExcelException");
        if (errorCode != null) {
            sb.append(" [").append(errorCode).append("]");
        }
        sb.append(": ").append(getMessage());
        return sb.toString();
    }
}