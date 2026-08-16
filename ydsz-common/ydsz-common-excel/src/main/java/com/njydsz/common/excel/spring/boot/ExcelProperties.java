package com.njydsz.common.excel.spring.boot;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.njydsz.common.excel.api.validator.DataValidator.ValidationMode;

/**
 * Excel 模块配置属性
 *
 * <p>对应 {@code ydsz.excel.*} 配置前缀，覆盖 {@link ExcelConfig} 的全部可配置项。
 * 在 {@code application.yml} 中配置示例：</p>
 *
 * <pre>{@code
 * ydsz:
 *   excel:
 *     read-buffer-size: 16384
 *     write-buffer-size: 16384
 *     default-date-format: "yyyy-MM-dd HH:mm:ss"
 *     automatic-trim: true
 *     use-fast-reader: true
 *     use-fast-writer: true
 *     streaming-parse-threshold-mb: 10
 *     max-read-file-size-mb: 100
 *     max-write-file-size-mb: 50
 *     compression-level: 1
 *     formula-injection-protection: true
 *     strict-number-conversion: false
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Validated
@ConfigurationProperties(prefix = "ydsz.excel")
public class ExcelProperties {

    /** 读取缓冲区大小（字节） */
    @Min(1024)
    private Integer readBufferSize;

    /** 写入缓冲区大小（字节） */
    @Min(1024)
    private Integer writeBufferSize;

    /** 默认日期格式 */
    private String defaultDateFormat;

    /** 默认数字格式 */
    private String defaultNumberFormat;

    /** 是否自动去除字符串首尾空格 */
    private Boolean automaticTrim;

    /** 是否使用快速解析器（零 POI 路径） */
    private Boolean useFastReader;

    /** 是否使用快速写入器（零 POI 路径） */
    private Boolean useFastWriter;

    /** 流式解析文件大小阈值（MB） */
    @Min(1)
    @Max(500)
    private Integer streamingParseThresholdMB;

    /** 最大读取文件大小（MB） */
    @Min(1)
    @Max(1024)
    private Integer maxReadFileSizeMB;

    /** 最大写入文件大小（MB） */
    @Min(1)
    @Max(512)
    private Integer maxWriteFileSizeMB;

    /** ZIP 压缩级别（-1~9） */
    @Min(-1)
    @Max(9)
    private Integer compressionLevel;

    /** 是否启用公式注入防护 */
    private Boolean formulaInjectionProtection;

    /** 是否启用严格数字转换 */
    private Boolean strictNumberConversion;

    /** 是否使用1904日期窗口（Mac版Excel兼容） */
    private Boolean use1904Windowing;

    /** 默认表头行号 */
    private Integer headRowNumber;

    /** SXSSF 写入缓存行数 */
    private Integer writeCacheSize;

    /** 数据校验模式（FAIL_FAST 或 COLLECT_ALL） */
    private ValidationMode validationMode;

    // ==================== Getters & Setters ====================

    public Integer getReadBufferSize() {
        return readBufferSize;
    }

    public void setReadBufferSize(Integer readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    public Integer getWriteBufferSize() {
        return writeBufferSize;
    }

    public void setWriteBufferSize(Integer writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    public String getDefaultDateFormat() {
        return defaultDateFormat;
    }

    public void setDefaultDateFormat(String defaultDateFormat) {
        this.defaultDateFormat = defaultDateFormat;
    }

    public String getDefaultNumberFormat() {
        return defaultNumberFormat;
    }

    public void setDefaultNumberFormat(String defaultNumberFormat) {
        this.defaultNumberFormat = defaultNumberFormat;
    }

    public Boolean getAutomaticTrim() {
        return automaticTrim;
    }

    public void setAutomaticTrim(Boolean automaticTrim) {
        this.automaticTrim = automaticTrim;
    }

    public Boolean getUseFastReader() {
        return useFastReader;
    }

    public void setUseFastReader(Boolean useFastReader) {
        this.useFastReader = useFastReader;
    }

    public Boolean getUseFastWriter() {
        return useFastWriter;
    }

    public void setUseFastWriter(Boolean useFastWriter) {
        this.useFastWriter = useFastWriter;
    }

    public Integer getStreamingParseThresholdMB() {
        return streamingParseThresholdMB;
    }

    public void setStreamingParseThresholdMB(Integer streamingParseThresholdMB) {
        this.streamingParseThresholdMB = streamingParseThresholdMB;
    }

    public Integer getMaxReadFileSizeMB() {
        return maxReadFileSizeMB;
    }

    public void setMaxReadFileSizeMB(Integer maxReadFileSizeMB) {
        this.maxReadFileSizeMB = maxReadFileSizeMB;
    }

    public Integer getMaxWriteFileSizeMB() {
        return maxWriteFileSizeMB;
    }

    public void setMaxWriteFileSizeMB(Integer maxWriteFileSizeMB) {
        this.maxWriteFileSizeMB = maxWriteFileSizeMB;
    }

    public Integer getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(Integer compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public Boolean getFormulaInjectionProtection() {
        return formulaInjectionProtection;
    }

    public void setFormulaInjectionProtection(Boolean formulaInjectionProtection) {
        this.formulaInjectionProtection = formulaInjectionProtection;
    }

    public Boolean getStrictNumberConversion() {
        return strictNumberConversion;
    }

    public void setStrictNumberConversion(Boolean strictNumberConversion) {
        this.strictNumberConversion = strictNumberConversion;
    }

    public Boolean getUse1904Windowing() {
        return use1904Windowing;
    }

    public void setUse1904Windowing(Boolean use1904Windowing) {
        this.use1904Windowing = use1904Windowing;
    }

    public Integer getHeadRowNumber() {
        return headRowNumber;
    }

    public void setHeadRowNumber(Integer headRowNumber) {
        this.headRowNumber = headRowNumber;
    }

    public Integer getWriteCacheSize() {
        return writeCacheSize;
    }

    public void setWriteCacheSize(Integer writeCacheSize) {
        this.writeCacheSize = writeCacheSize;
    }

    public ValidationMode getValidationMode() {
        return validationMode;
    }

    public void setValidationMode(ValidationMode validationMode) {
        this.validationMode = validationMode;
    }
}
