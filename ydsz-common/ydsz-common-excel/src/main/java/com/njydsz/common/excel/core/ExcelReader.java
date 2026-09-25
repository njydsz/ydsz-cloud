package com.njydsz.common.excel.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.api.validator.DataValidator;
import com.njydsz.common.excel.api.validator.RowRule;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.core.metrics.ExcelMetrics;
import com.njydsz.common.excel.core.reader.ColumnMetadata;
import com.njydsz.common.excel.core.reader.HeaderAnalyzer;
import com.njydsz.common.excel.core.reader.InputSourceDetector;
import com.njydsz.common.excel.core.reader.sax.SuperFastExcelReader;
import com.njydsz.common.excel.exception.ExcelReadException;
import com.njydsz.common.excel.support.mh.MHFieldAccessor;

/**
 * Excel 读取器 — 统一门面（零 POI 依赖）。
 *
 * <p>自 v26.10.01 起，底层完全委托 {@link SuperFastExcelReader}，不再依赖 Apache POI。
 * 提供链式 API（sheet、headRowNumber、includeColumnFiledNames 等），全部能力由 SuperFastExcelReader 实现。
 *
 * <h3>读取流程</h3>
 *
 * <ol>
 *   <li><b>格式识别</b> - 根据文件扩展名或输入流魔数判断 xlsx 格式</li>
 *   <li><b>输入源适配</b> - 文件路径 / File 对象直接使用；InputStream 先落盘到临时文件</li>
 *   <li><b>Sheet 定位</b> - 解析 workbook.xml + rels 找到目标 Sheet 条目</li>
 *   <li>手工 ZIP/XML 流式解析 Sheet 数据，BoundedInputStream 解压限流防护</li>
 *   <li>逐行解析并通过 MethodHandle 反射设置对象属性</li>
 *   <li>触发 ReadListener 回调通知每行数据</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 基本读取
 * ExcelFacade.read("demo.xlsx", User.class)
 *     .sheet("用户数据")
 *     .doRead(new ReadListener<User>() {
 *         &#64;Override
 *         public void onData(AnalysisContext context, User data) {
 *             log.info("读取到用户: {}", data.getName());
 *         }
 *     });
 *
 * // 便捷 lambda
 * ExcelFacade.read("demo.xlsx", User.class, (context, user) -> {
 *     saveToDatabase(user);
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ExcelFacade
 * @see ReadListener
 * @see ReadMetadata
 * @see AnalysisContext
 * @see SuperFastExcelReader
 */
public class ExcelReader {

  /** 日志记录器 */
  private static final Logger LOG = LoggerFactory.getLogger(ExcelReader.class);

  /** 字节到MB的换算常量（1024 × 1024） */
  private static final long BYTES_PER_MB = 1024L * 1024L;

  /** 读取配置元数据 */
  private final ReadMetadata metadata;

  /** 分析上下文 */
  private final AnalysisContext context;

  /** 已注册的监听器列表 */
  private final List<ReadListener<?>> listeners;

  /** 自定义行级校验规则列表 */
  private final List<RowRule<Object>> customRules;

  /** 高性能列元数据缓存 */
  private ColumnMetadata[] columnMetadataArray;

  /** 表头分析器 */
  private final HeaderAnalyzer headerAnalyzer;

  /** 输入源检测器 */
  private final InputSourceDetector inputSourceDetector;

  /** 批量读取大小 */
  private int batchSize = 0;

  /** 批量数据缓冲区 */
  private List<Object> batchBuffer;

  /**
   * 构造函数。
   *
   * @param metadata 读取配置元数据
   */
  public ExcelReader(ReadMetadata metadata) {
    this.metadata = metadata;
    this.context = new AnalysisContext(metadata);
    this.listeners = new ArrayList<>(4);
    this.customRules = new ArrayList<>(4);
    this.headerAnalyzer = new HeaderAnalyzer(metadata);
    this.inputSourceDetector = new InputSourceDetector(metadata);
  }

  // ==================== Sheet 选择配置 ====================

  /**
   * 使用默认配置读取。
   *
   * @return 当前读取器实体
   */
  public ExcelReader sheet() {
    return this;
  }

  /**
   * 指定要读取的 Sheet 名称。
   *
   * @param sheetName Sheet 名称
   * @return 当前读取器实体
   */
  public ExcelReader sheet(String sheetName) {
    metadata.setSheetName(sheetName);
    return this;
  }

  /**
   * 指定要读取的 Sheet 序号（从 0 开始）。
   *
   * @param sheetNo Sheet 序号
   * @return 当前读取器实体
   */
  public ExcelReader sheet(int sheetNo) {
    metadata.setSheetIndex(sheetNo);
    return this;
  }

  /**
   * 指定表头行号。
   *
   * @param headRowNumber 表头行号（从 1 开始）
   * @return 当前读取器实体
   */
  public ExcelReader headRowNumber(int headRowNumber) {
    metadata.setHeadRowNumber(headRowNumber);
    return this;
  }

  /**
   * 注册读取监听器。
   *
   * @param listener 数据监听器
   * @return 当前读取器实体
   */
  public <T> ExcelReader registerReadListener(ReadListener<T> listener) {
    if (listener != null) {
      this.listeners.add(listener);
    }
    return this;
  }

  /**
   * 注册自定义行级校验规则。
   *
   * @param rules 校验规则列表
   * @return 当前读取器实体
   */
  @SuppressWarnings("unchecked")
  public ExcelReader registerCustomRules(List<RowRule<Object>> rules) {
    if (rules != null) {
      this.customRules.addAll(rules);
    }
    return this;
  }

  /**
   * 设置批量读取大小。
   *
   * @param batchSize 批量大小
   * @return 当前读取器实体
   */
  public ExcelReader batchSize(int batchSize) {
    this.batchSize = batchSize;
    return this;
  }

  /**
   * 设置是否跳过空行。
   *
   * @param skipEmptyRows true 跳过空行
   * @return 当前读取器实体
   */
  public ExcelReader skipEmptyRows(boolean skipEmptyRows) {
    metadata.setIsSkipEmptyRows(skipEmptyRows);
    return this;
  }

  /**
   * 设置是否校验列数。
   *
   * @param checkColumnCount true 校验列数
   * @return 当前读取器实体
   */
  public ExcelReader checkColumnCount(boolean checkColumnCount) {
    metadata.setIsCheckColumnCount(checkColumnCount);
    return this;
  }

  /**
   * 设置期望的列数。
   *
   * @param expectedColumnCount 期望列数
   * @return 当前读取器实体
   */
  public ExcelReader expectedColumnCount(int expectedColumnCount) {
    metadata.setExpectedColumnCount(expectedColumnCount);
    return this;
  }

  /**
   * 设置最大读取行数。
   *
   * @param maxRows 最大行数
   * @return 当前读取器实体
   */
  public ExcelReader maxRows(int maxRows) {
    metadata.setMaxRows(maxRows);
    return this;
  }

  /**
   * 设置日期格式。
   *
   * @param dateFormat 日期格式
   * @return 当前读取器实体
   */
  public ExcelReader dateFormat(String dateFormat) {
    metadata.setDateFormat(dateFormat);
    return this;
  }

  /**
   * 设置数字格式。
   *
   * @param numberFormat 数字格式
   * @return 当前读取器实体
   */
  public ExcelReader numberFormat(String numberFormat) {
    metadata.setNumberFormat(numberFormat);
    return this;
  }

  /**
   * 是否自动去除字符串首尾空格。
   *
   * @param automaticTrim true 启用自动去空格
   * @return 当前读取器实体
   */
  public ExcelReader automaticTrim(boolean automaticTrim) {
    ExcelConfig config = metadata.getExcelConfig();
    if (config == null) {
      config = ExcelConfig.builder().automaticTrim(automaticTrim).build();
      metadata.setExcelConfig(config);
    } else {
      // ExcelConfig 是不可变对象，需要重建
      metadata.setExcelConfig(
          ExcelConfig.builder()
              .readBufferSize(config.getReadBufferSize())
              .writeBufferSize(config.getWriteBufferSize())
              .automaticTrim(automaticTrim)
              .defaultDateFormat(config.getDefaultDateFormat())
              .defaultNumberFormat(config.getDefaultNumberFormat())
              .maxReadCacheSize(config.getMaxReadCacheSize())
              .maxReadFileSizeMB(config.getMaxReadFileSizeMB())
              .maxWriteFileSizeMB(config.getMaxWriteFileSizeMB())
              .formulaInjectionProtection(config.getIsFormulaInjectionProtection())
              .compressionLevel(config.getCompressionLevel())
              .use1904Windowing(config.getIsUse1904Windowing())
              .headRowNumber(config.getHeadRowNumber())
              .validationMode(config.getValidationMode())
              .build());
    }
    return this;
  }

  /**
   * 设置 Excel 全局配置。
   *
   * @param config Excel 配置
   * @return 当前读取器实体
   */
  public ExcelReader config(ExcelConfig config) {
    metadata.setExcelConfig(config);
    return this;
  }

  /**
   * 排除指定字段。
   *
   * @param excludeColumnFiledNames 要排除的字段名集合
   * @return 当前读取器实体
   */
  public ExcelReader excludeColumnFiledNames(Set<String> excludeColumnFiledNames) {
    metadata.setExcludeColumnFiledNames(excludeColumnFiledNames);
    return this;
  }

  /**
   * 排除指定字段。
   *
   * @param excludeColumnFiledNames 要排除的字段名数组
   * @return 当前读取器实体
   */
  public ExcelReader excludeColumnFiledNames(String... excludeColumnFiledNames) {
    Set<String> set = new HashSet<>(Arrays.asList(excludeColumnFiledNames));
    return excludeColumnFiledNames(set);
  }

  /**
   * 只包含指定字段。
   *
   * @param includeColumnFiledNames 要包含的字段名集合
   * @return 当前读取器实体
   */
  public ExcelReader includeColumnFiledNames(Set<String> includeColumnFiledNames) {
    metadata.setIncludeColumnFiledNames(includeColumnFiledNames);
    return this;
  }

  /**
   * 只包含指定字段。
   *
   * @param includeColumnFiledNames 要包含的字段名数组
   * @return 当前读取器实体
   */
  public ExcelReader includeColumnFiledNames(String... includeColumnFiledNames) {
    Set<String> set = new HashSet(Arrays.asList(includeColumnFiledNames));
    return includeColumnFiledNames(set);
  }

  // ==================== 便捷读取方法 ====================

  /**
   * 便捷方法：一次性读取所有数据到列表。
   *
   * <p>适用于小数据量场景。大文件场景请使用 {@code doRead(ReadListener)} 流式处理。
   *
   * @param <T> 数据类型
   * @param fileName 文件路径
   * @param clazz 映射类型
   * @param dataConsumer 数据消费者
   * @param <T> 数据类型
   */
  public static <T> void read(String fileName, Class<T> clazz, ReadListener<T> dataConsumer) {
    ReadMetadata metadata = new ReadMetadata();
    metadata.setFilePath(fileName);
    metadata.setClazz(clazz);
    new ExcelReader(metadata).doRead(dataConsumer);
  }

  // ==================== 核心读取方法 ====================

  /**
   * 执行读取（无监听器版本）。
   */
  public void doRead() {
    doRead(null);
  }

  /**
   * 执行读取（带监听器版本）。
   *
   * <p>核心读取方法，全部委托 {@link SuperFastExcelReader}：
   *
   * <ul>
   *   <li>文件源（filePath / File）：直接使用 ZipFile 随机访问</li>
   *   <li>InputStream 源：先落盘到临时文件再读取（InputStream 不可随机访问）</li>
   *   <li>手工 ZIP/XML 流式解析 + BoundedInputStream 解压限流防护（zip bomb 防护）</li>
   * </ul>
   *
   * @param listener 数据监听器，可为 null（使用前请先调用 registerReadListener）
   * @param <T> 泛型参数
   * @throws ExcelReadException 读取过程中发生业务异常时抛出
   * @throws RuntimeException 其他未预期异常
   */
  public <T> void doRead(ReadListener<T> listener) {
    long startTime = System.nanoTime();
    try {
      if (listener != null) {
        listeners.add(listener);
      }
      notifyStart();

      // 检测文件格式
      boolean isXlsx = detectXlsxFormat();

      ExcelConfig config =
          metadata.getExcelConfig() != null ? metadata.getExcelConfig() : ExcelConfig.defaults();

      // 文件大小校验
      validateFileSize(config);

      if (!isXlsx) {
        throw ExcelReadException.invalidFormat(
            metadata.getFilePath() != null ? metadata.getFilePath() : "input",
            "当前版本仅支持 .xlsx 格式，不支持 .xls。请将文件另存为 .xlsx 格式后重试。");
      }

      // 委托 SuperFastExcelReader 完成全部读取
      SuperFastExcelReader superFastReader = createSuperFastReader(config);
      executeRead(superFastReader);

      notifyEnd();
      ExcelMetrics.recordRead(
          Duration.ofNanos(System.nanoTime() - startTime),
          context.getCurrentRow(),
          "super_fast",
          true);

    } catch (ExcelReadException e) {
      LOG.error("Excel 读取业务异常", e);
      ExcelMetrics.recordRead(
          Duration.ofNanos(System.nanoTime() - startTime),
          context.getCurrentRow(),
          "super_fast",
          false);
      throw e;
    } catch (OutOfMemoryError e) {
      LOG.error("Excel 读取内存溢出", e);
      ExcelMetrics.recordRead(
          Duration.ofNanos(System.nanoTime() - startTime),
          context.getCurrentRow(),
          "super_fast",
          false);
      throw ExcelReadException.outOfMemory(e);
    } catch (Exception e) {
      LOG.error("Excel 读取未知异常", e);
      ExcelMetrics.recordRead(
          Duration.ofNanos(System.nanoTime() - startTime),
          context.getCurrentRow(),
          "super_fast",
          false);
      throw ExcelReadException.ioError(context.getCurrentRow(), e);
    }
  }

  /**
   * 读取全部数据到列表。
   *
   * @param <T> 数据类型
   * @return 数据列表
   */
  public <T> List<T> doReadAll() {
    List<T> result = new ArrayList<>(16);
    doRead(
        new ReadListener<T>() {
          @Override
          public void onStart(AnalysisContext context) {}

          @Override
          public void onData(AnalysisContext context, T data) {
            result.add(data);
          }

          @Override
          public void onEnd(AnalysisContext context) {}
        });
    return result;
  }

  // ==================== 私有方法 ====================

  /**
   * 检测输入源是否为 xlsx 格式。
   *
   * @return true 表示为 xlsx 格式
   */
  private boolean detectXlsxFormat() {
    String filePath = metadata.getFilePath();
    if (filePath != null) {
      return filePath.toLowerCase().endsWith(".xlsx");
    }
    if (metadata.getFile() != null) {
      String fileName = metadata.getFile().getName();
      return fileName != null && fileName.toLowerCase().endsWith(".xlsx");
    }
    if (metadata.getInputStream() != null) {
      return inputSourceDetector.detectXlsxFormat(metadata.getInputStream());
    }
    throw ExcelReadException.fileNotFound("unknown");
  }

  /**
   * 校验文件大小是否超过限制。
   *
   * @param config Excel 配置
   */
  private void validateFileSize(ExcelConfig config) {
    int maxFileSizeMB = config.getMaxReadFileSizeMB();
    String filePath = metadata.getFilePath();

    if (filePath != null) {
      File file = new File(filePath);
      long fileSizeMB = file.length() / BYTES_PER_MB;
      if (fileSizeMB > maxFileSizeMB) {
        throw ExcelReadException.fileTooLarge(fileSizeMB, maxFileSizeMB);
      }
    } else if (metadata.getFile() != null) {
      long fileSizeMB = metadata.getFile().length() / BYTES_PER_MB;
      if (fileSizeMB > maxFileSizeMB) {
        throw ExcelReadException.fileTooLarge(fileSizeMB, maxFileSizeMB);
      }
    }
    // InputStream 模式下无法提前校验大小，由 SuperFastExcelReader 的 BoundedInputStream 兜底
  }

  /**
   * 创建并配置 SuperFastExcelReader 实例。
   *
   * @param config Excel 配置
   * @return 配置完毕的 SuperFastExcelReader 实例
   */
  private SuperFastExcelReader createSuperFastReader(ExcelConfig config) {
    SuperFastExcelReader superFastReader = new SuperFastExcelReader();
    superFastReader.setColumnMetadataArray(columnMetadataArray);

    // 元数据工厂：由 SheetXmlReader 收集表头后按注解规则惰性构建
    if (metadata.getClazz() != null) {
      superFastReader.setMetadataFactory(
          headerNames ->
              headerAnalyzer.analyzeClassMetadataFromNames(headerNames, new HashMap<>(16)));
      superFastReader.setInstantiator(MHFieldAccessor.getInstantiator(metadata.getClazz()));
    }

    superFastReader.setContext(context);
    superFastReader.setListeners(listeners);
    // headRowNumber 语义为 1-based 表头行号（1=第一行是表头），fast 引擎内部使用 0-based 索引
    superFastReader.setHeadRowNumber(Math.max(0, metadata.getHeadRowNumber() - 1));

    Integer maxRows = metadata.getMaxRows();
    if (maxRows != null && maxRows > 0) {
      superFastReader.setMaxRows(maxRows);
    }

    superFastReader.setExcelConfig(config);
    superFastReader.setSheetName(metadata.getSheetName());
    superFastReader.setSheetIndex(metadata.getSheetIndex());
    superFastReader.setIsSkipEmptyRows(Boolean.TRUE.equals(metadata.getIsSkipEmptyRows()));
    superFastReader.setCustomRules(customRules);

    return superFastReader;
  }

  /**
   * 根据输入源类型选择调用方式并执行读取。
   *
   * @param superFastReader 已配置的读取器实例
   * @throws Exception 读取异常
   */
  private void executeRead(SuperFastExcelReader superFastReader) throws Exception {
    String filePath = metadata.getFilePath();

    if (filePath != null) {
      superFastReader.read(Path.of(filePath));
    } else if (metadata.getFile() != null) {
      superFastReader.read(metadata.getFile().toPath());
    } else {
      InputStream is = metadata.getInputStream();
      if (is == null) {
        throw ExcelReadException.fileNotFound("unknown");
      }
      superFastReader.read(is);
    }
  }

  // ==================== 监听器通知方法 ====================

  private void notifyStart() {
    for (ReadListener<?> listener : listeners) {
      listener.onStart(context);
    }
  }

  /**
   * 通知所有监听器读取结束。
   *
   * <p>刷新剩余批次数据并触发 onEnd 回调。
   */
  @SuppressWarnings("unchecked")
  private void notifyEnd() {
    if (batchBuffer != null && !batchBuffer.isEmpty()) {
      for (ReadListener<?> listener : listeners) {
        ((ReadListener<Object>) listener).onBatchData(context, batchBuffer);
      }
      batchBuffer.clear();
    }
    for (ReadListener<?> listener : listeners) {
      listener.onEnd(context);
    }
  }
}
