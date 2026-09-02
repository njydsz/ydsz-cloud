package com.njydsz.common.excel.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.excel.api.validator.DataValidator.ValidationMode;

/**
 * Excel 模块配置属性
 *
 * <p>通过 {@code ydsz.excel.*} 前缀绑定 application.yml 中的配置项。 所有字段均有默认值，用户仅需覆盖需要自定义的部分。
 *
 * <p>P2-12 修复：补齐 {@code use1904Windowing} / {@code validationMode} / {@code
 * maxReadCacheSize} 三个 ExcelConfig 预留配置的绑定——此前这些配置项在 ExcelConfig
 * 中存在但 ExcelProperties 未声明，配置了不生效。
 *
 * @author ydsz-team
 * @since 26.09.01
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

  /** 是否使用快速读取（零 POI），默认 false（POI 兼容路径，正确性优先） */
  private Boolean useFastReader = false;

  /** 是否使用快速写入（零 POI），默认 false（POI 兼容路径，正确性优先） */
  private Boolean useFastWriter = false;

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

  /** 是否使用 1904 日期窗口（Mac Excel 兼容），默认 false（1900 窗口） */
  private Boolean use1904Windowing = false;

  /** 数据校验模式，默认 FAIL_FAST（遇错即抛）；可选 COLLECT_ALL（全量收集后抛） */
  private ValidationMode validationMode = ValidationMode.FAIL_FAST;

  /** 读缓存大小（条），默认 1024 */
  private Integer maxReadCacheSize = 1024;

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

  public Boolean getUse1904Windowing() {
    return use1904Windowing;
  }

  public void setUse1904Windowing(Boolean use1904Windowing) {
    this.use1904Windowing = use1904Windowing;
  }

  public ValidationMode getValidationMode() {
    return validationMode;
  }

  public void setValidationMode(ValidationMode validationMode) {
    this.validationMode = validationMode;
  }

  public Integer getMaxReadCacheSize() {
    return maxReadCacheSize;
  }

  public void setMaxReadCacheSize(Integer maxReadCacheSize) {
    this.maxReadCacheSize = maxReadCacheSize;
  }
}
