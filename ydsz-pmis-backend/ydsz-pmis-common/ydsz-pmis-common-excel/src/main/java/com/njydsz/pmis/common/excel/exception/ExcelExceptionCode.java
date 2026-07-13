package com.njydsz.pmis.common.excel.exception;

import com.njydsz.pmis.common.exception.enums.ExceptionCode;

/**
 * Excel 模块异常码枚举
 *
 * <p>定义 Excel 读写过程中所有业务异常的错误码和国际化消息键。
 * 实现统一异常码接口，与 common-exception 模块无缝集成。</p>
 *
 * <h3>错误码命名规范</h3>
 * <ul>
 *   <li>E01xxx - 读取异常</li>
 *   <li>E02xxx - 写入异常</li>
 *   <li>E03xxx - 转换异常</li>
 *   <li>E04xxx - 配置异常</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ExcelExceptionCode implements ExceptionCode {

    // ==================== 读取异常 E01xxx ====================
    READ_FILE_NOT_FOUND("E01001", "excel.read.fileNotFound"),
    READ_INVALID_FORMAT("E01002", "excel.read.invalidFormat"),
    READ_SHEET_NOT_FOUND("E01003", "excel.read.sheetNotFound"),
    READ_CONVERSION_FAILED("E01004", "excel.read.conversionFailed"),
    READ_ANNOTATION_ERROR("E01005", "excel.read.annotationError"),
    READ_OUT_OF_MEMORY("E01006", "excel.read.outOfMemory"),
    READ_VALIDATION_FAILED("E01007", "excel.read.validationFailed"),
    READ_FILE_TOO_LARGE("E01008", "excel.read.fileTooLarge"),
    READ_IO_ERROR("E01009", "excel.read.ioError"),

    // ==================== 写入异常 E02xxx ====================
    WRITE_FILE_ACCESS_FAILED("E02001", "excel.write.fileAccessFailed"),
    WRITE_INSUFFICIENT_SPACE("E02002", "excel.write.insufficientSpace"),
    WRITE_FILE_LOCKED("E02003", "excel.write.fileLocked"),
    WRITE_ANNOTATION_ERROR("E02004", "excel.write.annotationError"),
    WRITE_FORMAT_ERROR("E02005", "excel.write.formatError"),
    WRITE_WORKBOOK_CREATE_FAILED("E02006", "excel.write.workbookCreateFailed"),
    WRITE_DATA_FAILED("E02007", "excel.write.dataFailed"),
    WRITE_FILE_TOO_LARGE("E02008", "excel.write.fileTooLarge"),
    WRITE_IO_ERROR("E02009", "excel.write.ioError"),

    // ==================== 转换异常 E03xxx ====================
    CONVERT_TYPE_MISMATCH("E03001", "excel.convert.typeMismatch"),
    CONVERT_DATE_FORMAT("E03002", "excel.convert.dateFormat"),
    CONVERT_NUMBER_FORMAT("E03003", "excel.convert.numberFormat"),
    CONVERT_ENUM_INVALID("E03004", "excel.convert.enumInvalid"),

    // ==================== 配置异常 E04xxx ====================
    CONFIG_INVALID_PARAMETER("E04001", "excel.config.invalidParameter"),
    CONFIG_BEAN_MAPPING("E04002", "excel.config.beanMapping"),
    ;

    private final String code;
    private final String key;

    ExcelExceptionCode(String code, String key) {
        this.code = code;
        this.key = key;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getKey() {
        return key;
    }
}
