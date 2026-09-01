package com.njydsz.common.excel.core.config;

import java.util.List;
import java.util.zip.Deflater;

import com.njydsz.common.excel.api.validator.DataValidator.ValidationMode;
import com.njydsz.common.excel.core.security.FormulaInjectionGuard;

/**
 * Excel 全局配置 — 不可变配置对象。
 *
 * <p>通过 {@link Builder} 或 {@link ExcelProperties#toExcelConfig()} 构建， 一旦创建不可修改。建议通过 {@link
 * WriteMetadata} / {@link ReadMetadata} 传递到读写组件。
 *
 * <h3>线程安全性</h3>
 *
 * <p>所有字段均为 {@code final}，天然线程安全，无需同步。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelConfig {

  /** 默认读取/写入缓冲区大小（字节） */
  private static final int DEFAULT_BUFFER_SIZE = 8192;

  /** 默认最大读取缓存条目数 */
  private static final int DEFAULT_MAX_READ_CACHE_SIZE = 1024;

  /** 默认流式解析阈值（MB） */
  private static final int DEFAULT_STREAMING_PARSE_THRESHOLD_MB = 10;

  /** 默认最大读取文件大小（MB） */
  private static final int DEFAULT_MAX_READ_FILE_SIZE_MB = 100;

  /** 默认最大写入文件大小（MB） */
  private static final int DEFAULT_MAX_WRITE_FILE_SIZE_MB = 50;

  /** 默认表头行号（从 1 计） */
  private static final int DEFAULT_HEAD_ROW_NUMBER = 1;

  /** 默认 SXSSF 内存保留行数窗口 */
  private static final int DEFAULT_WRITE_CACHE_SIZE = 100;

  private final int readBufferSize;
  private final int writeBufferSize;
  private final boolean automaticTrim;
  private final String defaultDateFormat;
  private final String defaultNumberFormat;
  private final int maxReadCacheSize;
  private final int streamingParseThresholdMB;
  private final boolean strictNumberConversion;
  private final int maxReadFileSizeMB;
  private final int maxWriteFileSizeMB;
  private final boolean formulaInjectionProtection;
  private final boolean useFastReader;
  private final boolean useFastWriter;
  private final int compressionLevel;
  private final boolean use1904Windowing;
  private final int headRowNumber;
  private final int writeCacheSize;
  private final ValidationMode validationMode;

  /** 私有构造函数，仅通过 {@link Builder} 构建。 */
  private ExcelConfig(Builder builder) {
    this.readBufferSize = builder.readBufferSize;
    this.writeBufferSize = builder.writeBufferSize;
    this.automaticTrim = builder.automaticTrim;
    this.defaultDateFormat = builder.defaultDateFormat;
    this.defaultNumberFormat = builder.defaultNumberFormat;
    this.maxReadCacheSize = builder.maxReadCacheSize;
    this.streamingParseThresholdMB = builder.streamingParseThresholdMB;
    this.strictNumberConversion = builder.strictNumberConversion;
    this.maxReadFileSizeMB = builder.maxReadFileSizeMB;
    this.maxWriteFileSizeMB = builder.maxWriteFileSizeMB;
    this.formulaInjectionProtection = builder.formulaInjectionProtection;
    this.useFastReader = builder.useFastReader;
    this.useFastWriter = builder.useFastWriter;
    this.compressionLevel = builder.compressionLevel;
    this.use1904Windowing = builder.use1904Windowing;
    this.headRowNumber = builder.headRowNumber;
    this.writeCacheSize = builder.writeCacheSize;
    this.validationMode = builder.validationMode;
  }

  public int getReadBufferSize() {
    return readBufferSize;
  }

  public int getWriteBufferSize() {
    return writeBufferSize;
  }

  public boolean isAutomaticTrim() {
    return automaticTrim;
  }

  public String getDefaultDateFormat() {
    return defaultDateFormat;
  }

  public String getDefaultNumberFormat() {
    return defaultNumberFormat;
  }

  public int getMaxReadCacheSize() {
    return maxReadCacheSize;
  }

  public int getStreamingParseThresholdMB() {
    return streamingParseThresholdMB;
  }

  public boolean isStrictNumberConversion() {
    return strictNumberConversion;
  }

  public int getMaxReadFileSizeMB() {
    return maxReadFileSizeMB;
  }

  public int getMaxWriteFileSizeMB() {
    return maxWriteFileSizeMB;
  }

  public boolean isFormulaInjectionProtection() {
    return formulaInjectionProtection;
  }

  public boolean isUseFastReader() {
    return useFastReader;
  }

  public boolean isUseFastWriter() {
    return useFastWriter;
  }

  /**
   * 获取被视为公式注入风险的单元格起始字符列表。
   *
   * @return 危险前缀列表（默认 {@code =}、{@code +}）
   */
  public static List<String> getFormulaInjectionPrefixes() {
    return FormulaInjectionGuard.getFormulaInjectionPrefixes();
  }

  /**
   * 判断字符串是否存在 CSV/Excel 公式注入风险。
   *
   * <p>纯检测，不改写入参；实际转义由 {@link #sanitizeFormulaInjection(String)} 完成。 委派给 {@link
   * FormulaInjectionGuard}，不受 {@code formulaInjectionProtection} 开关影响。
   *
   * @param value 待检测的单元格文本，可为 {@code null}
   * @return {@code true} 表示以危险前缀开头，导出后可能被 Excel 当作公式执行
   */
  public boolean isPotentialFormulaInjection(String value) {
    return FormulaInjectionGuard.isPotentialFormulaInjection(value);
  }

  /**
   * 对存在公式注入风险的文本做转义处理。
   *
   * <p>委派给 {@link FormulaInjectionGuard}：命中危险前缀时加前导字符使其被 Excel 当作纯文本，未命中则原样返回。
   *
   * @param value 待处理的单元格文本，可为 {@code null}
   * @return 转义后的安全文本
   */
  public String sanitizeFormulaInjection(String value) {
    return FormulaInjectionGuard.sanitizeFormulaInjection(value);
  }

  public int getCompressionLevel() {
    return compressionLevel;
  }

  public boolean isUse1904Windowing() {
    return use1904Windowing;
  }

  public int getHeadRowNumber() {
    return headRowNumber;
  }

  public ValidationMode getValidationMode() {
    return validationMode;
  }

  public int getWriteCacheSize() {
    return writeCacheSize;
  }

  /**
   * 创建新的 {@link Builder} 实例。
   *
   * @return 新的 Builder 实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 创建默认配置实例。
   *
   * @return 默认 ExcelConfig
   */
  public static ExcelConfig defaults() {
    return new Builder().build();
  }

  /** {@link ExcelConfig} 的流式构建器。 */
  public static final class Builder {

    private int readBufferSize = DEFAULT_BUFFER_SIZE;
    private int writeBufferSize = DEFAULT_BUFFER_SIZE;
    private boolean automaticTrim = true;
    private String defaultDateFormat = "yyyy-MM-dd HH:mm:ss";
    private String defaultNumberFormat = "#,##0.00";
    private int maxReadCacheSize = DEFAULT_MAX_READ_CACHE_SIZE;
    private int streamingParseThresholdMB = DEFAULT_STREAMING_PARSE_THRESHOLD_MB;
    private boolean strictNumberConversion = false;
    private int maxReadFileSizeMB = DEFAULT_MAX_READ_FILE_SIZE_MB;
    private int maxWriteFileSizeMB = DEFAULT_MAX_WRITE_FILE_SIZE_MB;
    private boolean formulaInjectionProtection = true;
    private boolean useFastReader = false;
    private boolean useFastWriter = false;
    private int compressionLevel = Deflater.BEST_SPEED;
    private boolean use1904Windowing = false;
    private int headRowNumber = DEFAULT_HEAD_ROW_NUMBER;
    private int writeCacheSize = DEFAULT_WRITE_CACHE_SIZE;
    private ValidationMode validationMode = ValidationMode.FAIL_FAST;

    private Builder() {}

    public Builder readBufferSize(int readBufferSize) {
      this.readBufferSize = readBufferSize;
      return this;
    }

    public Builder writeBufferSize(int writeBufferSize) {
      this.writeBufferSize = writeBufferSize;
      return this;
    }

    public Builder automaticTrim(boolean automaticTrim) {
      this.automaticTrim = automaticTrim;
      return this;
    }

    public Builder defaultDateFormat(String defaultDateFormat) {
      this.defaultDateFormat = defaultDateFormat;
      return this;
    }

    public Builder defaultNumberFormat(String defaultNumberFormat) {
      this.defaultNumberFormat = defaultNumberFormat;
      return this;
    }

    public Builder maxReadCacheSize(int maxReadCacheSize) {
      this.maxReadCacheSize = maxReadCacheSize;
      return this;
    }

    public Builder streamingParseThresholdMB(int streamingParseThresholdMB) {
      this.streamingParseThresholdMB = streamingParseThresholdMB;
      return this;
    }

    public Builder strictNumberConversion(boolean strictNumberConversion) {
      this.strictNumberConversion = strictNumberConversion;
      return this;
    }

    public Builder maxReadFileSizeMB(int maxReadFileSizeMB) {
      this.maxReadFileSizeMB = maxReadFileSizeMB;
      return this;
    }

    public Builder maxWriteFileSizeMB(int maxWriteFileSizeMB) {
      this.maxWriteFileSizeMB = maxWriteFileSizeMB;
      return this;
    }

    public Builder formulaInjectionProtection(boolean formulaInjectionProtection) {
      this.formulaInjectionProtection = formulaInjectionProtection;
      return this;
    }

    public Builder useFastReader(boolean useFastReader) {
      this.useFastReader = useFastReader;
      return this;
    }

    public Builder useFastWriter(boolean useFastWriter) {
      this.useFastWriter = useFastWriter;
      return this;
    }

    public Builder compressionLevel(int compressionLevel) {
      this.compressionLevel = compressionLevel;
      return this;
    }

    public Builder use1904Windowing(boolean use1904Windowing) {
      this.use1904Windowing = use1904Windowing;
      return this;
    }

    public Builder headRowNumber(int headRowNumber) {
      this.headRowNumber = headRowNumber;
      return this;
    }

    public Builder writeCacheSize(int writeCacheSize) {
      this.writeCacheSize = writeCacheSize;
      return this;
    }

    public Builder validationMode(ValidationMode validationMode) {
      this.validationMode = validationMode;
      return this;
    }

    /**
     * 构建 {@link ExcelConfig} 实例。
     *
     * @return 已装配全部字段的 {@link ExcelConfig} 新实例
     */
    public ExcelConfig build() {
      return new ExcelConfig(this);
    }
  }
}
