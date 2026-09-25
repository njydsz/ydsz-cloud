package com.njydsz.common.excel.core.config;

import java.util.List;
import java.util.zip.Deflater;

import com.njydsz.common.excel.api.validator.DataValidator.ValidationMode;
import com.njydsz.common.excel.core.security.FormulaInjectionGuard;
import com.njydsz.common.excel.core.config.EngineType;

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
 * @since 26.09.01
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
  private final boolean isAutomaticTrim;
  private final String defaultDateFormat;
  private final String defaultNumberFormat;
  private final int maxReadCacheSize;
  private final int streamingParseThresholdMB;
  private final boolean isStrictNumberConversion;
  private final int maxReadFileSizeMB;
  private final int maxWriteFileSizeMB;
  private final boolean isFormulaInjectionProtection;
  private final boolean isUseFastReader;
  private final boolean isUseFastWriter;
  /**
   * 写引擎显式选择。默认 {@link EngineType#AUTO}（行为等价于旧版 {@link #isUseFastWriter} 逻辑）。
   *
   * <p>显式设为 {@link EngineType#SUPER_FAST} 时，能力超出手工引擎范围抛异常而非静默降级。
   * 显式设为 {@link EngineType#POI_STREAMING} 时始终走 POI 路径。
   */
  private final EngineType engineType;
  private final int compressionLevel;
  private final boolean isUse1904Windowing;
  private final int headRowNumber;
  private final int writeCacheSize;
  private final ValidationMode validationMode;

  /** 私有构造函数，仅通过 {@link Builder} 构建。 */
  private ExcelConfig(Builder builder) {
    this.readBufferSize = builder.readBufferSize;
    this.writeBufferSize = builder.writeBufferSize;
    this.isAutomaticTrim = builder.isAutomaticTrim;
    this.defaultDateFormat = builder.defaultDateFormat;
    this.defaultNumberFormat = builder.defaultNumberFormat;
    this.maxReadCacheSize = builder.maxReadCacheSize;
    this.streamingParseThresholdMB = builder.streamingParseThresholdMB;
    this.isStrictNumberConversion = builder.isStrictNumberConversion;
    this.maxReadFileSizeMB = builder.maxReadFileSizeMB;
    this.maxWriteFileSizeMB = builder.maxWriteFileSizeMB;
    this.isFormulaInjectionProtection = builder.isFormulaInjectionProtection;
    this.isUseFastReader = builder.isUseFastReader;
    this.isUseFastWriter = builder.isUseFastWriter;
    this.engineType =
        builder.engineType != null ? builder.engineType : EngineType.AUTO;
    this.compressionLevel = builder.compressionLevel;
    this.isUse1904Windowing = builder.isUse1904Windowing;
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

  public boolean getIsAutomaticTrim() {
    return isAutomaticTrim;
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

  public boolean getIsStrictNumberConversion() {
    return isStrictNumberConversion;
  }

  public int getMaxReadFileSizeMB() {
    return maxReadFileSizeMB;
  }

  public int getMaxWriteFileSizeMB() {
    return maxWriteFileSizeMB;
  }

  public boolean getIsFormulaInjectionProtection() {
    return isFormulaInjectionProtection;
  }

  public boolean getIsUseFastReader() {
    return isUseFastReader;
  }

  public boolean getIsUseFastWriter() {
    return isUseFastWriter;
  }

  /** 获取写引擎类型。 */
  public EngineType getEngineType() {
    return engineType;
  }

  /**
   * 获取被视为公式注入风险的单元格起始字符列表。
   *
   * @return 危险前缀列表（默认为 OWASP 完整前缀集 {@code =}、{@code +}、{@code -}、{@code @}、{@code \t}、{@code \r}）
   */
  public static List<String> getFormulaInjectionPrefixes() {
    return FormulaInjectionGuard.getFormulaInjectionPrefixes();
  }

  /**
   * 判断字符串是否存在 CSV/Excel 公式注入风险。
   *
   * <p>纯检测，不改写入参；实际转义由 {@link #sanitizeFormulaInjection(String)}（CSV 路径）或
   * {@link #sanitizeForXlsx(String)}（XLSX 路径）完成。 委派给 {@link
   * FormulaInjectionGuard}，不受 {@code isFormulaInjectionProtection} 开关影响。
   *
   * @param value 待检测的单元格文本，可为 {@code null}
   * @return {@code true} 表示以危险前缀开头，导出后可能被 Excel 当作公式执行
   */
  public boolean isPotentialFormulaInjection(String value) {
    return FormulaInjectionGuard.isPotentialFormulaInjection(value);
  }

  /**
   * 对存在公式注入风险的文本做转义处理（CSV 路径策略）。
   *
   * <p>委派给 {@link FormulaInjectionGuard}：命中危险前缀时加前导撇号（Excel 导入 CSV
   * 时隐藏显示）。XLSX 写入路径请改用 {@link #sanitizeForXlsx(String)}。
   *
   * @param value 待处理的单元格文本，可为 {@code null}
   * @return 转义后的安全文本
   */
  public String sanitizeFormulaInjection(String value) {
    return FormulaInjectionGuard.sanitizeFormulaInjection(value);
  }

  /**
   * 对存在公式注入风险的文本做转义处理（XLSX 路径策略）。
   *
   * <p>委派给 {@link FormulaInjectionGuard}：命中危险前缀时加前导空格（OOXML 字符串单元格
   * 本身免疫求值，此为纵深防御；撇号在 XLSX 中会字面显示）。
   *
   * @param value 待处理的单元格文本，可为 {@code null}
   * @return 转义后的安全文本
   */
  public String sanitizeForXlsx(String value) {
    return FormulaInjectionGuard.sanitizeForXlsx(value);
  }

  public int getCompressionLevel() {
    return compressionLevel;
  }

  public boolean getIsUse1904Windowing() {
    return isUse1904Windowing;
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
   * 基于已有的 {@link ExcelConfig} 创建 {@link Builder} 实例。
   *
   * <p>新 Builder 的所有字段预填充为 source 的当前值，便于在已有配置上做增量覆盖。
   * source 为 {@code null} 时行为等同于 {@link #builder()}（全部取默认值）。
   *
   * <h3>使用示例</h3>
   * <pre>{@code
   * ExcelConfig base = ExcelConfig.defaults();
   * ExcelConfig fast = ExcelConfig.builder(base)
   *     .useFastReader(true)
   *     .useFastWriter(true)
   *     .build();
   * }</pre>
   *
   * @param source 拷贝源配置，可为 {@code null}
   * @return 预填充源字段值的 Builder 实例
   */
  public static Builder builder(ExcelConfig source) {
    Builder b = new Builder();
    if (source == null) {
      return b;
    }
    b.readBufferSize = source.readBufferSize;
    b.writeBufferSize = source.writeBufferSize;
    b.isAutomaticTrim = source.isAutomaticTrim;
    b.defaultDateFormat = source.defaultDateFormat;
    b.defaultNumberFormat = source.defaultNumberFormat;
    b.maxReadCacheSize = source.maxReadCacheSize;
    b.streamingParseThresholdMB = source.streamingParseThresholdMB;
    b.isStrictNumberConversion = source.isStrictNumberConversion;
    b.maxReadFileSizeMB = source.maxReadFileSizeMB;
    b.maxWriteFileSizeMB = source.maxWriteFileSizeMB;
    b.isFormulaInjectionProtection = source.isFormulaInjectionProtection;
    b.isUseFastReader = source.isUseFastReader;
    b.isUseFastWriter = source.isUseFastWriter;
    b.engineType = source.engineType;
    b.compressionLevel = source.compressionLevel;
    b.isUse1904Windowing = source.isUse1904Windowing;
    b.headRowNumber = source.headRowNumber;
    b.writeCacheSize = source.writeCacheSize;
    b.validationMode = source.validationMode;
    return b;
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
    private boolean isAutomaticTrim = true;
    private String defaultDateFormat = "yyyy-MM-dd HH:mm:ss";
    private String defaultNumberFormat = "#,##0.00";
    private int maxReadCacheSize = DEFAULT_MAX_READ_CACHE_SIZE;
    private int streamingParseThresholdMB = DEFAULT_STREAMING_PARSE_THRESHOLD_MB;
    private boolean isStrictNumberConversion = false;
    private int maxReadFileSizeMB = DEFAULT_MAX_READ_FILE_SIZE_MB;
    private int maxWriteFileSizeMB = DEFAULT_MAX_WRITE_FILE_SIZE_MB;
    private boolean isFormulaInjectionProtection = true;
    private boolean isUseFastReader = false;
  private boolean isUseFastWriter = false;
  /** 写引擎显式选择。默认 {@link EngineType#AUTO}。 */
  private EngineType engineType = EngineType.AUTO;
  private int compressionLevel = Deflater.BEST_SPEED;
    private boolean isUse1904Windowing = false;
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

    public Builder automaticTrim(boolean isAutomaticTrim) {
      this.isAutomaticTrim = isAutomaticTrim;
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

    public Builder strictNumberConversion(boolean isStrictNumberConversion) {
      this.isStrictNumberConversion = isStrictNumberConversion;
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

    public Builder formulaInjectionProtection(boolean isFormulaInjectionProtection) {
      this.isFormulaInjectionProtection = isFormulaInjectionProtection;
      return this;
    }

    public Builder useFastReader(boolean isUseFastReader) {
      this.isUseFastReader = isUseFastReader;
      return this;
    }

    public Builder useFastWriter(boolean isUseFastWriter) {
      this.isUseFastWriter = isUseFastWriter;
      return this;
    }

    /**
     * 显式选择写引擎类型。
     *
     * @param engineType 引擎类型，{@code null} 自动回退到默认值 {@link EngineType#AUTO}
     * @return Builder 自身
     */
    public Builder engineType(EngineType engineType) {
      this.engineType = engineType != null ? engineType : EngineType.AUTO;
      return this;
    }

    public Builder compressionLevel(int compressionLevel) {
      this.compressionLevel = compressionLevel;
      return this;
    }

    public Builder use1904Windowing(boolean isUse1904Windowing) {
      this.isUse1904Windowing = isUse1904Windowing;
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
