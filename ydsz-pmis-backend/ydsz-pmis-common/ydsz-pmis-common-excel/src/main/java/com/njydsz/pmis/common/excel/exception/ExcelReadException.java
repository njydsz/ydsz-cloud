package com.njydsz.pmis.common.excel.exception;

/**
 * Excel 读取异常类
 *
 * <p>封装 Excel 文件读取过程中的各类错误，包括文件解析失败、
 * 数据类型转换错误、注解配置错误等场景。</p>
 *
 * <h3>常见异常场景</h3>
 * <ul>
 *   <li>文件不存在或无法访问</li>
 *   <li>文件格式损坏或不是有效的 Excel 文件</li>
 *   <li>Sheet 不存在或名称不匹配</li>
 *   <li>数据类型转换失败（如日期格式错误）</li>
 *   <li>字段注解配置错误</li>
 *   <li>内存不足（文件过大）</li>
 * </ul>
 *
 * <h3>错误码规范</h3>
 * <ul>
 *   <li>READ_001 - 文件访问错误</li>
 *   <li>READ_002 - 文件格式错误</li>
 *   <li>READ_003 - Sheet 不存在</li>
 *   <li>READ_004 - 数据类型转换错误</li>
 *   <li>READ_005 - 注解配置错误</li>
 *   <li>READ_006 - 内存不足</li>
 *   <li>READ_007 - 数据验证失败</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * try {
 *     ExcelFacade.read("data.xlsx", User.class)
 *         .sheet(0)
 *         .headRowNumber(1)
 *         .doRead(new ReadListener<User>() {
 *             @Override
 *             public void onData(AnalysisContext context, User data) {
 *                 // 处理每行数据
 *             }
 *         });
 * } catch (ExcelReadException e) {
 *     log.error("读取失败 - 错误码: {}, 消息: {}", 
 *         e.getErrorCode(), e.getMessage(), e);
 *     
 *     // 根据错误码进行针对性处理
 *     if ("READ_001".equals(e.getErrorCode())) {
 *         // 文件不存在处理
 *     } else if ("READ_004".equals(e.getErrorCode())) {
 *         // 数据转换错误处理
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 * @see ExcelException
 * @see com.njydsz.pmis.common.excel.core.ExcelReader
 */
public class ExcelReadException extends ExcelException {

    private static final long serialVersionUID = 1L;

    /** 发生错误的行号 */
    private Integer rowNumber;

    /** 发生错误的列号 */
    private Integer columnNumber;

    /** 原始单元格值 */
    private transient Object rawCellValue;

    /**
     * 构造读取异常
     */
    public ExcelReadException() {
        super();
    }

    /**
     * 构造带错误信息的读取异常
     *
     * @param message 错误描述
     */
    public ExcelReadException(String message) {
        super(message);
    }

    /**
     * 构造带错误信息和原因的读取异常
     *
     * @param message 错误描述
     * @param cause 原始异常
     */
    public ExcelReadException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造带错误码和错误信息的读取异常
     *
     * @param errorCode 错误码
     * @param message 错误描述
     */
    public ExcelReadException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造带错误码、错误信息和原因的读取异常
     *
     * @param errorCode 错误码
     * @param message 错误描述
     * @param cause 原始异常
     */
    public ExcelReadException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 获取发生错误的行号
     *
     * @return 行号（从1开始），未知返回 null
     */
    public Integer getRowNumber() {
        return rowNumber;
    }

    /**
     * 设置发生错误的行号
     *
     * @param rowNumber 行号
     */
    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    /**
     * 获取发生错误的列号
     *
     * @return 列号（从0开始），未知返回 null
     */
    public Integer getColumnNumber() {
        return columnNumber;
    }

    /**
     * 设置发生错误的列号
     *
     * @param columnNumber 列号
     */
    public void setColumnNumber(Integer columnNumber) {
        this.columnNumber = columnNumber;
    }

    /**
     * 获取原始单元格值
     *
     * @return 转换失败时的原始单元格值
     */
    public Object getRawCellValue() {
        return rawCellValue;
    }

    /**
     * 设置原始单元格值
     *
     * @param rawCellValue 原始单元格值
     */
    public void setRawCellValue(Object rawCellValue) {
        this.rawCellValue = rawCellValue;
    }

    /**
     * 创建文件不存在的异常
     *
     * @param filePath 文件路径
     * @return 读取异常实例
     */
    public static ExcelReadException fileNotFound(String filePath) {
        ExcelReadException ex = new ExcelReadException("READ_001",
            "Excel文件不存在或无法访问: " + filePath);
        ex.setContext(new Object[]{filePath});
        return ex;
    }

    /**
     * 创建文件格式错误的异常
     *
     * @param filePath 文件路径
     * @param reason 错误原因
     * @return 读取异常实例
     */
    public static ExcelReadException invalidFormat(String filePath, String reason) {
        ExcelReadException ex = new ExcelReadException("READ_002",
            "Excel文件格式无效: " + reason);
        ex.setContext(new Object[]{filePath, reason});
        return ex;
    }

    /**
     * 创建数据类型转换异常的静态工厂方法
     *
     * @param row 行号
     * @param col 列号
     * @param rawValue 原始值
     * @param targetType 目标类型
     * @param cause 转换异常
     * @return 读取异常实例
     */
    public static ExcelReadException conversionFailed(int row, int col, 
            Object rawValue, Class<?> targetType, Throwable cause) {
        ExcelReadException ex = new ExcelReadException("READ_004",
            String.format("数据类型转换失败: 行=%d, 列=%d, 值=%s, 目标类型=%s",
                row, col, rawValue, targetType.getSimpleName()), cause);
        ex.setRowNumber(row);
        ex.setColumnNumber(col);
        ex.setRawCellValue(rawValue);
        return ex;
    }

    /**
     * 创建数据验证失败的异常
     *
     * @param row 行号
     * @param fieldName 字段名称
     * @param value 当前值
     * @param reason 失败原因
     * @return 读取异常实例
     */
    public static ExcelReadException validationFailed(int row, String fieldName, Object value, String reason) {
        ExcelReadException ex = new ExcelReadException("READ_007",
            String.format("数据验证失败: 行=%d, 字段=%s, 值=%s, 原因=%s",
                row, fieldName, value, reason));
        ex.setRowNumber(row);
        return ex;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ExcelReadException");
        String errorCode = getErrorCode();
        if (errorCode != null) {
            sb.append(" [").append(errorCode).append("]");
        }
        sb.append(": ").append(getMessage());
        if (rowNumber != null) {
            sb.append(" [行号=").append(rowNumber);
            if (columnNumber != null) {
                sb.append(", 列号=").append(columnNumber);
            }
            sb.append("]");
        }
        return sb.toString();
    }
}