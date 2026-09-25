package com.njydsz.common.excel.core.config;

import java.util.List;
import java.util.zip.Deflater;

import com.njydsz.common.excel.api.validator.DataValidator.ValidationMode;
import com.njydsz.common.excel.core.security.FormulaInjectionGuard;

/**
 * Excel 全局配置 — 不可变配置对象。
 *
 * <p>通过 {@link Builder} 或 {@link ExcelProperties#toExcelConfig()} 构建，一旦创建不可修改。
 * 建议通过 {@link com.njydsz.common.excel.core.metadata.WriteMetadata} /
 * {@link com.njydsz.common.excel.core.metadata.ReadMetadata} 传递到读写组件。
 *
 * <p>自 v26.10.01 起，底层仅使用 SuperFast 零 POI 引擎，所有与 POI 相关的配置项
 * （引擎选择、SXSSF 窗口大小、严格数字转换等）已移除。
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

  /** 默认最大读取文件大小（MB） */
  private static final int DEFAULT_MAX_READ_FILE_SIZE_MB = 100;

  /** 默认最大写入文件大小（MB） */
  private static final int DEFAULT_MAX_WRITE_FILE_SIZE_MB = 50;

  /** 默认表头行号（从 1 计） */
  private static final int DEFAULT_HEAD_ROW_NUMBER = 1;

  private final int readBufferSize;
  private final int writeBufferSize;
  private final boolean isAutomaticTrim;
  private final String defaultDateFormat;
  private final String defaultNumberFormat;
  private final int maxReadCacheSize;
  private final int maxReadFileSizeMB;
  private final int maxWriteFileSizeMB;
  private final boolean isFormulaInjectionProtection;
  private final int compressionLevel;
  private final boolean isUse1904Windowing;
  private final int headRowNumber;
  private final ValidationMode validationMode;

  /** 私有构造函数，仅通过 {@link Builder} 构建。 */
  private ExcelConfig(Builder builder) {
    this.readBufferSize = builder.readBufferSize;
    this.writeBufferSize = builder.writeBufferSize;
    this.isAutomaticTrim = builder.isAutomaticTrim;
    this.defaultDateFormat = builder.defaultDateFormat;
    this.defaultNumberFormat = builder.defaultNumberFormat;
    this.maxReadCacheSize = builder.maxReadCacheSize;
    this.maxReadFileSizeMB = builder.maxReadFileSizeMB;
    this.maxWriteFileSizeMB = builder.maxWriteFileSizeMB;
    this.isFormulaInjectionProtection = builder.isFormulaInjectionProtection;
    this.compressionLevel = builder.compressionLevel;
    this.isUse1904Windowing = builder.isUse1904Windowing;
    this.headRowNumber = builder.headRowNumber;
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

  public int getMaxReadFileSizeMB() {
    return maxReadFileSizeMB;
  }

  public int getMaxWriteFileSizeMB() {
    return maxWriteFileSizeMB;
  }

  public boolean getIsFormulaInjectionProtection() {
    return isFormulaInjectionProtection;
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
   * {@link #sanitizeForXlsx(String)}（XLSX 路径）完成。
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
   * @param value 待处理的单元格文本，可为 {@code null}
   * @return 转义后的安全文本
   */
  public String sanitizeFormulaInjection(String value) {
    return FormulaInjectionGuard.sanitizeFormulaInjection(value);
  }

  /**
   * 对存在公式注入风险的文本做转义处理（XLSX 路径策略）。
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
    b.maxReadFileSizeMB = source.maxReadFileSizeMB;
    b.maxWriteFileSizeMB = source.maxWriteFileSizeMB;
    b.isFormulaInjectionProtection = source.isFormulaInjectionProtection;
    b.compressionLevel = source.compressionLevel;
    b.isUse1904Windowing = source.isUse1904Windowing;
    b.headRowNumber = source.headRowNumber;
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

  /**
   * 创建面向性能的极速模式配置 — 大文件限制放宽到 500MB、大缓冲区、宽松校验（COLLECT_ALL）。
   *
   * <p>适用场景：大数据量导出（10 万行+）、内部系统对账、ETL 批处理。
   *
   * @return 极速模式 ExcelConfig
   */
  public static ExcelConfig fastMode() {
    return new Builder()
        .maxReadFileSizeMB(500)
        .readBufferSize(65536)
        .validationMode(ValidationMode.COLLECT_ALL)
        .build();
  }

  /**
   * 创建面向安全的严格模式配置 — 最大文件 10MB、公式注入防护、FAIL_FAST 校验。
   *
   * <p>适用场景：面向外部用户的文件上传解析、合规审计场景、不可信数据源。
   *
   * @return 安全模式 ExcelConfig
   */
  public static ExcelConfig safeMode() {
    return new Builder()
        .maxReadFileSizeMB(10)
        .maxWriteFileSizeMB(10)
        .formulaInjectionProtection(true)
        .validationMode(ValidationMode.FAIL_FAST)
        .build();
  }

  /** {@link ExcelConfig} 的流式构建器。 */
  public static final class Builder {

    private int readBufferSize = DEFAULT_BUFFER_SIZE;
    private int writeBufferSize = DEFAULT_BUFFER_SIZE;
    private boolean isAutomaticTrim = true;
    private String defaultDateFormat = "yyyy-MM-dd HH:mm:ss";
    private String defaultNumberFormat = "#,##0.00";
    private int maxReadCacheSize = DEFAULT_MAX_READ_CACHE_SIZE;
    private int maxReadFileSizeMB = DEFAULT_MAX_READ_FILE_SIZE_MB;
    private int maxWriteFileSizeMB = DEFAULT_MAX_WRITE_FILE_SIZE_MB;
    private boolean isFormulaInjectionProtection = true;
    private int compressionLevel = Deflater.BEST_SPEED;
    private boolean isUse1904Windowing = false;
    private int headRowNumber = DEFAULT_HEAD_ROW_NUMBER;
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
