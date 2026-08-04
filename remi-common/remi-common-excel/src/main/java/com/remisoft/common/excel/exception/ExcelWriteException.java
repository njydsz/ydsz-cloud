package com.remisoft.common.excel.exception;

/**
 * Excel 写入异常类
 *
 * <p>封装 Excel 文件写入过程中的各类错误，包括文件创建失败、
 * 样式配置错误、数据写入错误等场景。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * try {
 *     ExcelFacade.write("output.xlsx", User.class)
 *         .sheet("用户数据")
 *         .headRowNumber(1)
 *         .doWrite(userList);
 * } catch (ExcelWriteException e) {
 *     log.error("写入失败 - 错误码: {}, 消息: {}", e.getCode(), e.getMessage(), e);
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 * @see ExcelException
 * @see ExcelExceptionCode
 */
public class ExcelWriteException extends ExcelException {

    private static final long serialVersionUID = 1L;

    /** 写入失败的数据对象索引 */
    private Integer dataIndex;

    /** 写入失败的字段名 */
    private String fieldName;

    public ExcelWriteException() {
        super();
    }

    public ExcelWriteException(String message) {
        super(message);
    }

    public ExcelWriteException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExcelWriteException(ExcelExceptionCode exceptionCode) {
        super(exceptionCode);
    }

    public ExcelWriteException(ExcelExceptionCode exceptionCode, String message) {
        super(exceptionCode, message);
    }

    public ExcelWriteException(ExcelExceptionCode exceptionCode, String message, Throwable cause) {
        super(exceptionCode, message, cause);
    }

    public Integer getDataIndex() {
        return dataIndex;
    }

    public void setDataIndex(Integer dataIndex) {
        this.dataIndex = dataIndex;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * 创建文件访问异常
     */
    public static ExcelWriteException fileAccessFailed(String filePath, String reason) {
        ExcelWriteException ex = new ExcelWriteException(ExcelExceptionCode.WRITE_FILE_ACCESS_FAILED,
            "文件访问失败: " + reason);
        ex.setContext(new Object[]{filePath, reason});
        return ex;
    }

    /**
     * 创建磁盘空间不足异常
     */
    public static ExcelWriteException insufficientSpace(String filePath,
            long requiredSpace, long availableSpace) {
        ExcelWriteException ex = new ExcelWriteException(ExcelExceptionCode.WRITE_INSUFFICIENT_SPACE,
            String.format("磁盘空间不足: 所需=%d字节, 可用=%d字节", requiredSpace, availableSpace));
        ex.setContext(new Object[]{filePath, requiredSpace, availableSpace});
        return ex;
    }

    /**
     * 创建注解配置错误异常
     */
    public static ExcelWriteException invalidAnnotation(Class<?> clazz,
            String fieldName, String reason) {
        ExcelWriteException ex = new ExcelWriteException(ExcelExceptionCode.WRITE_ANNOTATION_ERROR,
            String.format("注解配置错误 [%s.%s]: %s",
                clazz.getSimpleName(), fieldName, reason));
        ex.setFieldName(fieldName);
        ex.setContext(new Object[]{clazz.getName(), fieldName, reason});
        return ex;
    }

    /**
     * 创建数据写入异常
     */
    public static ExcelWriteException dataWriteFailed(int index,
            String fieldName, Object value, Throwable cause) {
        ExcelWriteException ex = new ExcelWriteException(ExcelExceptionCode.WRITE_DATA_FAILED,
            String.format("数据写入失败: 索引=%d, 字段=%s, 值=%s", index, fieldName, value),
            cause);
        ex.setDataIndex(index);
        ex.setFieldName(fieldName);
        return ex;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ExcelWriteException");
        String code = getCode();
        if (code != null) {
            sb.append(" [").append(code).append("]");
        }
        sb.append(": ").append(getMessage());
        if (dataIndex != null) {
            sb.append(" [数据索引=").append(dataIndex);
            if (fieldName != null) {
                sb.append(", 字段=").append(fieldName);
            }
            sb.append("]");
        }
        return sb.toString();
    }
}
