package com.njydsz.pmis.common.excel.exception;

/**
 * Excel 写入异常类
 *
 * <p>封装 Excel 文件写入过程中的各类错误，包括文件创建失败、
 * 样式配置错误、数据写入错误等场景。</p>
 *
 * <h3>常见异常场景</h3>
 * <ul>
 *   <li>文件路径无效或目录不存在</li>
 *   <li>磁盘空间不足</li>
 *   <li>文件被其他程序占用</li>
 *   <li>注解配置错误（如字段类型不支持）</li>
 *   <li>数据格式化失败</li>
 *   <li>工作簿创建失败</li>
 * </ul>
 *
 * <h3>错误码规范</h3>
 * <ul>
 *   <li>WRITE_001 - 文件访问错误</li>
 *   <li>WRITE_002 - 磁盘空间不足</li>
 *   <li>WRITE_003 - 文件被占用</li>
 *   <li>WRITE_004 - 注解配置错误</li>
 *   <li>WRITE_005 - 数据格式化错误</li>
 *   <li>WRITE_006 - 工作簿创建失败</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * try {
 *     ExcelFacade.write("output.xlsx", User.class)
 *         .sheet("用户数据")
 *         .headRowNumber(1)
 *         .doWrite(userList);
 * } catch (ExcelWriteException e) {
 *     log.error("写入失败 - 错误码: {}, 消息: {}", 
 *         e.getErrorCode(), e.getMessage(), e);
 *     
 *     // 根据错误码进行针对性处理
 *     if ("WRITE_001".equals(e.getErrorCode())) {
 *         // 文件路径错误处理
 *     } else if ("WRITE_002".equals(e.getErrorCode())) {
 *         // 磁盘空间不足处理
 *     }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @email pmis-dev@njydsz.com
 * @version 1.0.0
 * @see ExcelException
 * @see com.njydsz.pmis.common.excel.core.ExcelWriter
 */
public class ExcelWriteException extends ExcelException {

    private static final long serialVersionUID = 1L;

    /** 写入失败的数据对象索引 */
    private Integer dataIndex;

    /** 写入失败的字段名 */
    private String fieldName;

    /**
     * 构造写入异常
     */
    public ExcelWriteException() {
        super();
    }

    /**
     * 构造带错误信息的写入异常
     *
     * @param message 错误描述
     */
    public ExcelWriteException(String message) {
        super(message);
    }

    /**
     * 构造带错误信息和原因的写入异常
     *
     * @param message 错误描述
     * @param cause 原始异常
     */
    public ExcelWriteException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造带错误码和错误信息的写入异常
     *
     * @param errorCode 错误码
     * @param message 错误描述
     */
    public ExcelWriteException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 构造带错误码、错误信息和原因的写入异常
     *
     * @param errorCode 错误码
     * @param message 错误描述
     * @param cause 原始异常
     */
    public ExcelWriteException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    /**
     * 获取写入失败的数据索引
     *
     * @return 数据索引，未知返回 null
     */
    public Integer getDataIndex() {
        return dataIndex;
    }

    /**
     * 设置写入失败的数据索引
     *
     * @param dataIndex 数据索引
     */
    public void setDataIndex(Integer dataIndex) {
        this.dataIndex = dataIndex;
    }

    /**
     * 获取写入失败的字段名
     *
     * @return 字段名，未知返回 null
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * 设置写入失败的字段名
     *
     * @param fieldName 字段名
     */
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * 创建文件访问异常的静态工厂方法
     *
     * @param filePath 文件路径
     * @param reason 错误原因
     * @return 写入异常实例
     */
    public static ExcelWriteException fileAccessFailed(String filePath, String reason) {
        ExcelWriteException ex = new ExcelWriteException("WRITE_001",
            "文件访问失败: " + reason);
        ex.setContext(new Object[]{filePath, reason});
        return ex;
    }

    /**
     * 创建磁盘空间不足异常的静态工厂方法
     *
     * @param filePath 文件路径
     * @param requiredSpace 所需空间（字节）
     * @param availableSpace 可用空间（字节）
     * @return 写入异常实例
     */
    public static ExcelWriteException insufficientSpace(String filePath, 
            long requiredSpace, long availableSpace) {
        ExcelWriteException ex = new ExcelWriteException("WRITE_002",
            String.format("磁盘空间不足: 所需=%d字节, 可用=%d字节", requiredSpace, availableSpace));
        ex.setContext(new Object[]{filePath, requiredSpace, availableSpace});
        return ex;
    }

    /**
     * 创建注解配置错误的静态工厂方法
     *
     * @param clazz 类名
     * @param fieldName 字段名
     * @param reason 错误原因
     * @return 写入异常实例
     */
    public static ExcelWriteException invalidAnnotation(Class<?> clazz, 
            String fieldName, String reason) {
        ExcelWriteException ex = new ExcelWriteException("WRITE_004",
            String.format("注解配置错误 [%s.%s]: %s", 
                clazz.getSimpleName(), fieldName, reason));
        ex.setFieldName(fieldName);
        ex.setContext(new Object[]{clazz.getName(), fieldName, reason});
        return ex;
    }

    /**
     * 创建数据写入异常的静态工厂方法
     *
     * @param index 数据索引
     * @param fieldName 字段名
     * @param value 写入的值
     * @param cause 原始异常
     * @return 写入异常实例
     */
    public static ExcelWriteException dataWriteFailed(int index, 
            String fieldName, Object value, Throwable cause) {
        ExcelWriteException ex = new ExcelWriteException("WRITE_005",
            String.format("数据写入失败: 索引=%d, 字段=%s, 值=%s", index, fieldName, value),
            cause);
        ex.setDataIndex(index);
        ex.setFieldName(fieldName);
        return ex;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ExcelWriteException");
        String errorCode = getErrorCode();
        if (errorCode != null) {
            sb.append(" [").append(errorCode).append("]");
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