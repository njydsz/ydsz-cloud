package com.njydsz.common.excel.exception;

/**
 * Excel 模块异常码枚举
 *
 * <p>自包含的错误码定义，不依赖全局异常体系。
 * 定义 Excel 读写过程中所有业务异常的错误码和国际化消息键。</p>
 *
 * <h3>错误码命名规范</h3>
 * <ul>
 *   <li>H01xxx - 读取异常</li>
 *   <li>H02xxx - 写入异常</li>
 *   <li>H03xxx - 转换异常</li>
 *   <li>H04xxx - 配置异常</li>
 * </ul>
 *
 * <h3>全局异常体系桥接</h3>
 * <p>本枚举自包含定义，不直接实现全局 {@code ExceptionCode} 接口。
 * 如需接入全局异常体系，可在调用方通过适配器模式桥接：</p>
 * <pre>{@code
 * // 适配器示例：将 ExcelExceptionCode 桥接到全局 ExceptionCode 接口
 * ExceptionCode adapt(ExcelExceptionCode excelCode) {
 *     return new ExceptionCode() {
 *         public String getCode() { return excelCode.getCode(); }
 *         public String getKey() { return excelCode.getKey(); }
 *         // ...
 *     };
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ExcelExceptionCode {

    // ==================== 读取异常 H01xxx ====================
    READ_FILE_NOT_FOUND("H01001", "excel.read.fileNotFound"),
    READ_INVALID_FORMAT("H01002", "excel.read.invalidFormat"),
    READ_SHEET_NOT_FOUND("H01003", "excel.read.sheetNotFound"),
    READ_CONVERSION_FAILED("H01004", "excel.read.conversionFailed"),
    READ_ANNOTATION_ERROR("H01005", "excel.read.annotationError"),
    READ_OUT_OF_MEMORY("H01006", "excel.read.outOfMemory"),
    READ_VALIDATION_FAILED("H01007", "excel.read.validationFailed"),
    READ_FILE_TOO_LARGE("H01008", "excel.read.fileTooLarge"),
    READ_IO_ERROR("H01009", "excel.read.ioError"),

    // ==================== 写入异常 H02xxx ====================
    WRITE_FILE_ACCESS_FAILED("H02001", "excel.write.fileAccessFailed"),
    WRITE_INSUFFICIENT_SPACE("H02002", "excel.write.insufficientSpace"),
    WRITE_FILE_LOCKED("H02003", "excel.write.fileLocked"),
    WRITE_ANNOTATION_ERROR("H02004", "excel.write.annotationError"),
    WRITE_FORMAT_ERROR("H02005", "excel.write.formatError"),
    WRITE_WORKBOOK_CREATE_FAILED("H02006", "excel.write.workbookCreateFailed"),
    WRITE_DATA_FAILED("H02007", "excel.write.dataFailed"),
    WRITE_FILE_TOO_LARGE("H02008", "excel.write.fileTooLarge"),
    WRITE_IO_ERROR("H02009", "excel.write.ioError"),

    // ==================== 转换异常 H03xxx ====================
    CONVERT_TYPE_MISMATCH("H03001", "excel.convert.typeMismatch"),
    CONVERT_DATE_FORMAT("H03002", "excel.convert.dateFormat"),
    CONVERT_NUMBER_FORMAT("H03003", "excel.convert.numberFormat"),
    CONVERT_ENUM_INVALID("H03004", "excel.convert.enumInvalid"),

    // ==================== 配置异常 H04xxx ====================
    CONFIG_INVALID_PARAMETER("H04001", "excel.config.invalidParameter"),
    CONFIG_BEAN_MAPPING("H04002", "excel.config.beanMapping");

    /** 错误码 */
    private final String code;

    /** 国际化消息键 */
    private final String key;

    ExcelExceptionCode(String code, String key) {
        this.code = code;
        this.key = key;
    }

    /**
     * 获取错误码字符串。
     *
     * @return 错误码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取国际化消息键。
     *
     * @return 国际化消息 key
     */
    public String getKey() {
        return key;
    }

    /**
     * 获取模块标识。
     *
     * @return 模块名称
     */
    public String getModule() {
        return "excel";
    }

    /**
     * 获取默认兜底消息。
     *
     * @return 默认消息描述
     */
    public String getMsg() {
        return key;
    }

    /**
     * HTTP 状态码映射。
     *
     * <p>读取/写入/转换异常映射为 400，配置异常映射为 500。</p>
     *
     * @return HTTP 状态码
     */
    public int getHttpStatus() {
        if (name().startsWith("CONFIG_")) {
            return 500;
        }
        return 400;
    }
}
