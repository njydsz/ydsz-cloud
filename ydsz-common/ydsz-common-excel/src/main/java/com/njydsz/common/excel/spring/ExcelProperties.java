package com.njydsz.common.excel.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Excel 模块配置属性
 *
 * <p>通过 {@code ydsz.excel.*} 前缀绑定 application.yml 中的配置项。
 * 所有字段均有默认值，用户仅需覆盖需要自定义的部分。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "ydsz.excel")
public class ExcelProperties {

    /** 读缓冲区大小（字节），默认 8192 */
    private Integer readBufferSize = 8192;

    /** 写缓冲区大小（字节），默认 8192 */
    private Integer writeBufferSize = 8192;

    /** 默认日期格式，默认 yyyy-MM-dd HH:mm:ss */
    private String defaultDateFormat = "yyyy-MM-dd HH:mm:ss";

    /** 默认数字格式，默认 #,##0.00 */
    private String defaultNumberFormat = "#,##0.00";

    /** 是否自动 trim 字符串，默认 true */
    private Boolean automaticTrim = true;

    /** 是否使用快速读取（零 POI），默认 true */
    private Boolean useFastReader = true;

    /** 是否使用快速写入（零 POI），默认 true */
    private Boolean useFastWriter = true;

    /** 流式解析阈值（MB），默认 10 */
    private Integer streamingParseThresholdMb = 10;

    /** 最大读取文件大小（MB），默认 100 */
    private Integer maxReadFileSizeMb = 100;

    /** 最大写入文件大小（MB），默认 50 */
    private Integer maxWriteFileSizeMb = 50;

    /** ZIP 压缩级别，默认 1 */
    private Integer compressionLevel = 1;

    /** 是否启用公式注入防护，默认 true */
    private Boolean formulaInjectionProtection = true;

    /** 是否使用严格数字转换，默认 false */
    private Boolean strictNumberConversion = false;

    /** 默认表头行号，默认 1 */
    private Integer headRowNumber = 1;

    /** SXSSF 写入缓存大小，默认 100 */
    private Integer writeCacheSize = 100;

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

    public Integer getStreamingParseThresholdMb() {
        return streamingParseThresholdMb;
    }

    public void setStreamingParseThresholdMb(Integer streamingParseThresholdMb) {
        this.streamingParseThresholdMb = streamingParseThresholdMb;
    }

    public Integer getMaxReadFileSizeMb() {
        return maxReadFileSizeMb;
    }

    public void setMaxReadFileSizeMb(Integer maxReadFileSizeMb) {
        this.maxReadFileSizeMb = maxReadFileSizeMb;
    }

    public Integer getMaxWriteFileSizeMb() {
        return maxWriteFileSizeMb;
    }

    public void setMaxWriteFileSizeMb(Integer maxWriteFileSizeMb) {
        this.maxWriteFileSizeMb = maxWriteFileSizeMb;
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
}
