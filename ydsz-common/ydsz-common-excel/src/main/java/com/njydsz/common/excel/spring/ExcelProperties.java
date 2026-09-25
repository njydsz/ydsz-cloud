package com.njydsz.common.excel.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.njydsz.common.excel.api.validator.DataValidator.ValidationMode;
import com.njydsz.common.excel.core.config.EngineType;
import com.njydsz.common.excel.core.config.ExcelConfig;

/**
 * Excel 模块配置属性
 *
 * <p>通过 {@code ydsz.excel.*} 前缀绑定 application.yml 中的配置项。 所有字段均有默认值，用户仅需覆盖需要自定义的部分。
 *
 * <p>P2-12 修复：补齐 {@code isUse1904Windowing} / {@code validationMode} / {@code
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
  private Boolean isAutomaticTrim = true;

  /** 是否使用快速读取（零 POI），默认 true（fast 引擎优先） */
  private Boolean isUseFastReader = true;

  /** 是否使用快速写入（零 POI），默认 true（fast 引擎优先） */
  private Boolean isUseFastWriter = true;

  /** 流式解析阈值（MB），默认 10 */
  private Integer streamingParseThresholdMb = 10;

  /** 最大读取文件大小（MB），默认 100 */
  private Integer maxReadFileSizeMb = 100;

  /** 最大写入文件大小（MB），默认 50 */
  private Integer maxWriteFileSizeMb = 50;

  /** ZIP 压缩级别，默认 1 */
  private Integer compressionLevel = 1;

  /** 是否启用公式注入防护，默认 true */
  private Boolean isFormulaInjectionProtection = true;

  /** 是否使用严格数字转换，默认 false */
  private Boolean isStrictNumberConversion = false;

  /** 默认表头行号，默认 1 */
  private Integer headRowNumber = 1;

  /** SXSSF 写入缓存大小，默认 100 */
  private Integer writeCacheSize = 100;

  /** 是否使用 1904 日期窗口（Mac Excel 兼容），默认 false（1900 窗口） */
  private Boolean isUse1904Windowing = false;

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

  public Boolean getIsAutomaticTrim() {
    return isAutomaticTrim;
  }

  public void setIsAutomaticTrim(Boolean isAutomaticTrim) {
    this.isAutomaticTrim = isAutomaticTrim;
  }

  public Boolean getIsUseFastReader() {
    return isUseFastReader;
  }

  public void setIsUseFastReader(Boolean isUseFastReader) {
    this.isUseFastReader = isUseFastReader;
  }

  public Boolean getIsUseFastWriter() {
    return isUseFastWriter;
  }

  public void setIsUseFastWriter(Boolean isUseFastWriter) {
    this.isUseFastWriter = isUseFastWriter;
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

  public Boolean getIsFormulaInjectionProtection() {
    return isFormulaInjectionProtection;
  }

  public void setIsFormulaInjectionProtection(Boolean isFormulaInjectionProtection) {
    this.isFormulaInjectionProtection = isFormulaInjectionProtection;
  }

  public Boolean getIsStrictNumberConversion() {
    return isStrictNumberConversion;
  }

  public void setIsStrictNumberConversion(Boolean isStrictNumberConversion) {
    this.isStrictNumberConversion = isStrictNumberConversion;
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

  public Boolean getIsUse1904Windowing() {
    return isUse1904Windowing;
  }

  public void setIsUse1904Windowing(Boolean isUse1904Windowing) {
    this.isUse1904Windowing = isUse1904Windowing;
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

  /**
   * 将 Spring {@code ydsz.excel.*} 配置桥接为不可变的 {@link ExcelConfig}。
   *
   * <p>所有 getter 均已做 null-safe 处理——{@code ExcelConfig.Builder} 提供合理的内置默认值，
   * 未配置的字段不需要显式设值。
   *
   * <p>引擎类型由 {@code isUseFastWriter} 隐式推导：fast writer 未启用时使用 {@link
   * EngineType#POI_STREAMING}，否则由 {@link EngineType#AUTO} 在运行期按文件大小自动选择。
   *
   * @return 根据当前属性构建的不可变 {@link ExcelConfig} 实例
   */
  public ExcelConfig toExcelConfig() {
    boolean fastReader = getOrElse(isUseFastReader, true);
    boolean fastWriter = getOrElse(isUseFastWriter, true);
    EngineType inferredEngine = fastWriter ? EngineType.AUTO : EngineType.POI_STREAMING;
    return ExcelConfig.builder()
        .readBufferSize(getOrElse(readBufferSize, 8192))
        .writeBufferSize(getOrElse(writeBufferSize, 8192))
        .automaticTrim(getOrElse(isAutomaticTrim, true))
        .defaultDateFormat(getOrElse(defaultDateFormat, "yyyy-MM-dd HH:mm:ss"))
        .defaultNumberFormat(getOrElse(defaultNumberFormat, "#,##0.00"))
        .maxReadCacheSize(getOrElse(maxReadCacheSize, 1024))
        .streamingParseThresholdMB(getOrElse(streamingParseThresholdMb, 10))
        .strictNumberConversion(getOrElse(isStrictNumberConversion, false))
        .maxReadFileSizeMB(getOrElse(maxReadFileSizeMb, 100))
        .maxWriteFileSizeMB(getOrElse(maxWriteFileSizeMb, 50))
        .formulaInjectionProtection(getOrElse(isFormulaInjectionProtection, true))
        .useFastReader(fastReader)
        .useFastWriter(fastWriter)
        .engineType(inferredEngine)
        .compressionLevel(getOrElse(compressionLevel, 1))
        .use1904Windowing(getOrElse(isUse1904Windowing, false))
        .headRowNumber(getOrElse(headRowNumber, 1))
        .writeCacheSize(getOrElse(writeCacheSize, 100))
        .validationMode(getOrElse(validationMode, ValidationMode.FAIL_FAST))
        .build();
  }

  /**
   * 返回属性的非空值，若为 {@code null} 则返回指定的默认值。
   *
   * @param value 属性原始值
   * @param defaultValue 属性为 {@code null} 时的回退值
   * @return value 不为 {@code null} 时返回 value，否则返回 defaultValue
   */
  private static <T> T getOrElse(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }
}
