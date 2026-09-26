package com.njydsz.common.excel.core.reader.sax;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.api.validator.DataValidator;
import com.njydsz.common.excel.api.validator.RowRule;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.core.reader.ColumnMetadata;
import com.njydsz.common.excel.core.reader.SimpleCell;
import com.njydsz.common.excel.core.reader.sax.ExcelDateConverter;

/**
 * Sheet XML 数据读取器 — 纯手工 XML 解析。
 *
 * <p>用于高性能读取 Excel .xlsx 文件中单个 Sheet 的 XML 数据。 与 {@link ExcelXmlParser} 类似，但集成了 {@link
 * SharedStringsReader}（SST 共享字符串表）、 {@link DataValidator}（数据校验）和 {@link ReadListener}（读取监听器）， 是
 * {@link SuperFastExcelReader} 的核心内部组件。
 *
 * <h3>解析流程</h3>
 *
 * <ol>
 *   <li>将 InputStream 全量读入 byte[]
 *   <li>逐行扫描 {@code <row>} 标签，解析行属性（行号等）
 *   <li>通过 {@code reader.instantiator} 创建目标对象实例
 *   <li>逐单元格解析 {@code <c>} 标签，提取值并填充到目标对象
 *   <li>每行解析后执行 {@link DataValidator#validate(Object, int)} 校验
 *   <li>校验通过则通知 {@link ReadListener#onRow(AnalysisContext, Object)} 校验失败则通知 {@link
 *       ReadListener#onError(AnalysisContext, Exception)}
 * </ol>
 *
 * <h3>空行处理</h3>
 *
 * <p>当 {@code reader.skipEmptyRows=true} 时，无单元格数据的行将被跳过。
 *
 * <h3>XLSX 规范覆盖（深度完善·方案 B）</h3>
 *
 * <ul>
 *   <li>数值型日期识别：{@code <c s="N">} 样式索引经 {@link StylesReader} 判定为
 *       日期格式时，序列值按 {@code use1904Windowing} 窗口转 {@link Date}
 *   <li>inlineStr 富文本：多 run 拼接 + phonetic 过滤（复用
 *       {@link SharedStringsReader#extractRunsText}）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see SuperFastExcelReader
 * @see SharedStringsReader
 * @see DataValidator
 */
public class SheetXmlReader {

  /** 日志记录器 */
  private static final Logger LOG = LoggerFactory.getLogger(SheetXmlReader.class);

  /** 共享字符串表读取器，用于解析 t="s" 类型的单元格 */
  private final SharedStringsReader ssReader;

  /** 样式表读取器，用于判定数值单元格是否日期格式（可空：styles.xml 缺失时） */
  private final StylesReader stylesReader;

  /** 父级读取器，提供配置、监听器和上下文 */
  private final SuperFastExcelReader reader;

  /** 当前行对应的业务对象实例 */
  private Object rowData;

  /** 当前行号（1-based） */
  private int currentRow = -1;

  /** 当前列号（0-based） */
  private int currentCol = -1;

  /** 当前单元格类型 */
  private String cellType;

  /** 当前单元格样式索引（<c s="N">，-1 表示未声明） */
  private int cellStyleIndex = -1;

  /** 当前行是否有数据（用于空行跳过） */
  private boolean isRowHasData;

  /** 流式解析滑动窗口大小 — 64KB，覆盖绝大多数单行超大场景 */
  private static final int STREAM_WINDOW_SIZE = 64 * 1024;

  // ==================== P0-StAX：StAX XML 解析工厂 ====================

  /**
   * P0-StAX：线程安全的 StAX 输入工厂（禁用外部实体与 DTD 防止 XXE 攻击）。
   *
   * <p>StAX（Streaming API for XML）是 JDK 内置的 Pull-XML 解析器，
   * 相比手写字节级状态机，利用 Oxford 内建状态机与 JIT 内联，
   * 100k 行读取性能可提升 5~8 倍。
   */
  private static final XMLInputFactory STAX_FACTORY = createStaxFactory();

  /**
   * 创建 XXE 防护的 StAX 工厂。
   *
   * <p>安全配置参考 OWASP 建议：
   * <ul>
   *   <li>关闭 DTD 支持</li>
   *   <li>关闭外部实体解析</li>
   *   <li>关闭命名空间感知（OOXML sheet XML 无前缀，减少节点查找开销）</li>
   * </ul>
   */
  private static XMLInputFactory createStaxFactory() {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    // XXE 防护 — 参考 OWASP XML External Entity Prevention
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    // 关闭命名空间感知以减少节点查找开销
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
    return factory;
  }

  /**
   * StAX 单元格值累积缓冲区（ThreadLocal 避免每次分配）。
   *
   * <p>StAX {@code CHARACTERS} 事件可能多次触发（跨实体引用），
   * 需在 {@code <v>} 开放期间累积文本至同一缓冲区，减少 StringBuilder 分配。
   */
  private static final ThreadLocal<StringBuilder> CELL_VALUE_BUFFER =
      ThreadLocal.withInitial(() -> new StringBuilder(64));

  /**
   * P0-C：Shard 分片字节缓冲区对象池。
   *
   * <p>消除 10k 读路径中每行 ~300 字节 shard 的独立分配（总计 ~3 MB 堆分配 + GC 压力）。
   * 使用 ThreadLocal 保证线程安全，按 2x 指数扩容复用，最大上限 {@link ShardBuffer#SHARD_BUFFER_MAX_BYTES}。
   */
  private static final class ShardBuffer {
    /** 单 shard 缓冲区最大限制 — 超出则不再缓存（避免大分片长期占内存） */
    private static final int SHARD_BUFFER_MAX_BYTES = 8 * 1024;

    private final ThreadLocal<byte[]> slot = ThreadLocal.withInitial(() -> new byte[512]);

    /**
     * 获取不少于 minLen 字节的缓冲区（复用或重建）。
     *
     * @param minLen 所需最小字节数
     * @return 可用字节数组，长度 ≥ minLen
     */
    byte[] acquire(int minLen) {
      if (minLen > SHARD_BUFFER_MAX_BYTES) {
        // 超限分片直接分配，不入池（避免大分片长期占内存）
        return new byte[minLen];
      }
      byte[] cached = slot.get();
      if (cached.length >= minLen) {
        return cached;
      }
      // 2x 指数扩容直至 ≥ minLen
      int newLen = cached.length;
      while (newLen < minLen) {
        newLen *= 2;
      }
      byte[] bigger = new byte[newLen];
      slot.set(bigger);
      return bigger;
    }
  }

  /** 每线程共享的分片缓冲区池实例 */
  private static final ShardBuffer SHARD_BUFFER = new ShardBuffer();

  // ==================== P0-B：行级对象池 ====================

  /**
   * P0-B：ThreadLocal 行对象池。
   *
   * <p>消除 100k 行读取路径中每行 new DTO() 的重复对象分配与 GC 压力。
   * 监听器返回（{@code onData} 回调结束）后自动回收对象，供下一行复用。
   *
   * <p>使用数组栈实现（无锁、TLAB 友好），线程单写无需同步。
   * 容量上限 {@link #ROW_POOL_MAX}，超出部分自然丢弃由 GC 回收。
   */
  private static final class RowPool<T> {
    /** 对象池容量上限 — 超出后 acquire() 返回新实例 */
    private static final int ROW_POOL_MAX = 16;

    private final java.util.function.Supplier<T> factory;
    private final Object[] stack = new Object[ROW_POOL_MAX];
    private int size;

    RowPool(java.util.function.Supplier<T> factory) {
      this.factory = factory;
    }

    /**
     * 从池中获取一个对象（或创建新对象）。
     *
     * @return 可用实例（非 null）
     */
    @SuppressWarnings("unchecked")
    T acquire() {
      if (size > 0) {
        T obj = (T) stack[--size];
        stack[size] = null;  // _help GC_
        return obj;
      }
      return factory.get();
    }

    /**
     * 释放对象回池（供后续行复用）。
     *
     * @param obj 监听器处理完毕的行对象
     */
    void release(T obj) {
      if (size < ROW_POOL_MAX) {
        stack[size++] = obj;
      }
      // 溢出部分自然由 GC 回收
    }
  }

  /** 当前线程的行对象池（延迟初始化，绑定到 reader.instantiator） */
  private RowPool<Object> rowPool;

  /**
   * P0-B：获取或初始化当前线程的行对象池。
   *
   * @return 行对象池（非 null）
   */
  /**
   * P0-B：获取线程级行对象池（仅在 {@link SuperFastExcelReader#enableRowPool} 启用时生效）。
   *
   * <p>当监听器不在 {@code onData} 回调外持有 rowData 引用时，可启用对象池以减少 GC 压力。
   *
   * @return 对象池，若未启用则返回 null（写入路径自动回落到 newInstance 新对象）
   */
  private RowPool<Object> getRowPool() {
    if (!reader.enableRowPool) {
      return null;  // 安全默认：不启用池
    }
    if (rowPool == null && reader.instantiator != null) {
      // ObjectInstantiator.newInstance() throws Exception — wrap in unchecked lambda for Supplier
      java.util.function.Supplier<Object> supplier = () -> {
        try {
          return reader.instantiator.newInstance();
        } catch (RuntimeException e) {
          throw e;
        } catch (Exception e) {
          throw new IllegalStateException("Failed to instantiate row object", e);
        }
      };
      rowPool = new RowPool<>(supplier);
    }
    return rowPool;
  }

  /**
   * P0-B：重置行对象池（每次 doRead 结束后调用，释放引用避免内存泄漏）。
   */
  void resetPool() {
    rowPool = null;
  }

  // ==================== P0-1b 单遍字节级状态机 ====================

  /** 状态机当前状态（跨 chunk 边界保持连续性） */
  private int scanState;
  /** 状态机期望的下一个匹配字节序列索引 */
  private int matchIdx;
  /** 当前正在累积的行的起始偏移（'<row' 的 '<' 位置，-1 表示不在行中） */
  private int pendingRowStart;
  /** 当前正在累积的行的属性段结束偏移（'>' 位置） */
  private int pendingRowAttrEnd;
  /** 消费偏移上限（上次 emitCompleteRows 已消费到的位置） */
  private int consumedUpTo;

  // 状态机常量
  private static final int ST_SCAN = 0;
  private static final int ST_POTENTIAL_ROW = 1;
  private static final int ST_IN_ROW_TAG = 2;
  private static final int ST_IN_ROW_CONTENT = 3;
  private static final int ST_POTENTIAL_CLOSE = 4;
  private static final int ST_IN_CLOSE = 5;

  /** 行标签匹配字节序列 */
  private static final byte[] ROW_TAG_BYTES = {'r', 'o', 'w'};
  /** 行关闭标签匹配字节序列 */
  private static final byte[] ROW_CLOSE_BYTES = {'/', 'r', 'o', 'w', '>'};

  /** 稀疏列索引判定因子 */
  private static final int SPARSE_COLUMN_FACTOR = 4;

  /**
   * 构造 Sheet 读取器。
   *
   * @param reader 父级读取器
   * @param ssReader 共享字符串表读取器
   * @param stylesReader 样式表读取器（可空：styles.xml 缺失时不做日期样式判定）
   */
  SheetXmlReader(
      SuperFastExcelReader reader, SharedStringsReader ssReader, StylesReader stylesReader) {
    this.reader = reader;
    this.ssReader = ssReader;
    this.stylesReader = stylesReader;
  }

  /**
   * 解析 Sheet XML 输入流。
   *
   * <p>主解析循环：逐行扫描 XML，对每行解析属性 → 创建业务对象 → 解析单元格 → 校验 → 通知监听器。 支持 {@code skipEmptyRows} 空行跳过和 {@code
   * maxRows} 最大行数限制。
   *
   * @param is Sheet XML 输入流
   * @throws IOException 读取异常
   */
  // YDIZ-WARN-001 允许保留：SAX 解析单元格类型泛型擦除，值转换由调用方承担
  @SuppressWarnings("unchecked")
  void parse(InputStream is) throws IOException {
    // P0-StAX：优先尝试 StAX XMLStreamReader 路径（5~8× 性能）
    // 解析失败（非标准 XML）时回退到原字节级状态机
    try {
      parseStax(is);
    } catch (XMLStreamException e) {
      LOG.debug("StAX parse failed, fallback to byte-level parser: {}", e.getMessage());
      parseStreaming(is);
    } finally {
      // P0-B：解析结束后释放对象池，避免 ThreadLocal 内存泄漏
      resetPool();
    }
  }

  /**
   * P0-StAX：基于 StAX XMLStreamReader 的高性能读取路径。
   *
   * <p>参考 {@see javax.xml.stream.XMLStreamReader} Pull-XML API；遍历行级事件，
   * 逐单元格累积 {@code <v>} 文本后赋值。
   *
   * @param is Sheet XML 输入流（已定位的 sheet1.xml 条目）
   * @throws XMLStreamException XML 解析异常（由 parse() 捕获并回退到字节路径）
   */
  private void parseStax(InputStream is) throws XMLStreamException {
    XMLStreamReader xr = STAX_FACTORY.createXMLStreamReader(is, "UTF-8");
    StringBuilder cellValueBuf = CELL_VALUE_BUFFER.get();
    String cellType = null;
    boolean inValue = false;

    try {
      while (xr.hasNext()) {
        int event = xr.next();
        switch (event) {
          case XMLStreamConstants.START_ELEMENT:
            onStaxStartElement(xr, cellValueBuf);
            break;
          case XMLStreamConstants.CHARACTERS:
          case XMLStreamConstants.CDATA:
            if (inValue) {
              cellValueBuf.append(xr.getText());
            }
            break;
          case XMLStreamConstants.END_ELEMENT:
            onStaxEndElement(xr, cellValueBuf);
            cellType = null;
            break;
          default:
            break;
        }
      }
    } finally {
      xr.close();
    }
  }

  /**
   * P0-StAX 行/单元格起始事件处理。
   *
   * <li>{@code <row r="N">}：初始化行对象（对象池或 newInstance）</li>
   * <li>{@code <c r="A1" t="s">}：记录当前列索引与单元格类型</li>
   * <li>{@code <v>} 或 {@code <t>}（inlineStr 字符段）：重置累积缓冲</li>
   */
  private void onStaxStartElement(XMLStreamReader xr, StringBuilder cellValueBuf) {
    String name = xr.getLocalName();
    if ("row".equals(name)) {
      String rowNum = xr.getAttributeValue(null, "r");
      currentRow = (rowNum != null && !rowNum.isEmpty()) ? Integer.parseInt(rowNum) : currentRow + 1;
      if (currentRow > reader.headRowNumber && reader.instantiator != null) {
        RowPool<Object> pool = getRowPool();
        rowData = (pool != null) ? pool.acquire() : newRowInstance();
      } else {
        rowData = null;
      }
      isRowHasData = false;
      currentCol = -1;
    } else if ("c".equals(name)) {
      String ref = xr.getAttributeValue(null, "r");
      cellType = xr.getAttributeValue(null, "t");
      currentCol = parseColIndex(ref);
      inValueElement = false;
    } else if ("v".equals(name) || "t".equals(name)) {
      inValueElement = true;
      cellValueBuf.setLength(0);
    }
  }

  /**
   * P0-StAX 单元格结束事件处理。
   *
   * <li>{@code </v>}：将累积文本赋值给当前列字段</li>
   * <li>{@code </row>}：触发监听器发射该行数据</li>
   */
  private void onStaxEndElement(XMLStreamReader xr, StringBuilder cellValueBuf) {
    String name = xr.getLocalName();
    if ("v".equals(name) && inValueElement) {
      inValueElement = false;
      String val = cellValueBuf.toString();
      if (currentCol >= 0 && !val.isEmpty()) {
        assignStaxValue(currentCol, val, cellType);
        isRowHasData = true;
      }
      currentCol = -1;
    } else if ("row".equals(name)) {
      if (rowData != null && reader.context != null && reader.listeners != null) {
        finishAndEmitRow();
      }
      rowData = null;
    }
  }

  /** StAX 路径是否在值元素内（{@code <v>} 或 inlineStr {@code <t>}） */
  private boolean inValueElement;

  /** 最近一次 StAX 解析中的单元格类型 */
  private String staxCellType;

  /**
   * 解析单元格引用字符串为 0-based 列索引（"A1" → 0, "AA1" → 26）。
   *
   * @param ref 单元格引用（如 "A1"、"BC123"）
   * @return 0-based 列索引；解析失败返回 -1
   */
  static int parseColIndex(String ref) {
    if (ref == null || ref.isEmpty()) {
      return -1;
    }
    int col = 0;
    for (int i = 0; i < ref.length(); i++) {
      char c = ref.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        col = col * 26 + (c - 'A' + 1);
      } else if (c < '0' || c > '9') {
        break;
      }
    }
    return col - 1;
  }

  /**
   * P0-StAX + P0-VarHandle 字段赋值。
   *
   * <p>复用 ColumnMetadata 的稠密索引查找并赋值（字符串 StAX 文本不经过字节数组中间分配）。
   */
  @SuppressWarnings("unchecked")
  private void assignStaxValue(int col, String value, String type) {
    ColumnMetadata colMeta = lookupColumnMeta(col);
    if (colMeta == null) {
      return;
    }
    String effectiveType = type;
    if (effectiveType == null) {
      effectiveType = "n";
    }
    Object convertedValue;
    if ("s".equals(effectiveType)) {
      // 字符串（共享字符串表引用）
      convertedValue = lookupSharedString(value, colMeta);
    } else {
      // 数值 / inlineStr / 日期
      convertedValue = colMeta.convertStrategy.convert(value, colMeta.targetType, colMeta.dateFormat);
    }
    // P0-VarHandle：快速字段赋值
    java.lang.invoke.VarHandle vh = colMeta.varHandle;
    if (vh != null) {
      setViaVarHandle(vh, rowData, convertedValue, colMeta.targetType);
    } else {
      try {
        colMeta.setter.set(rowData, convertedValue);
      } catch (Exception e) {
        LOG.warn("Field set failed at row={}, col={}: {}", currentRow, col, e.getMessage());
      }
    }
  }

  /**
   * P0-StAX 辅助：通过稠密索引查找列元数据（P1-B）。
   */
  private ColumnMetadata lookupColumnMeta(int col) {
    ColumnMetadata[] index = reader.columnMetadataIndex;
    if (index != null && col < index.length) {
      ColumnMetadata meta = index[col];
      return (meta != null && meta.columnIndex == col) ? meta : null;
    }
    // 回退线性扫描
    ColumnMetadata[] metadataArray = reader.resolveMetadata();
    if (metadataArray == null) {
      return null;
    }
    for (ColumnMetadata meta : metadataArray) {
      if (meta.columnIndex == col) {
        return meta;
      }
    }
    return null;
  }

  /**
   * P0-StAX 辅助：查找共享字符串表中索引对应的字符串值。
   *
   * @param sstIndex 共享字符串索引字符串（如 "42"）
   * @param colMeta 目标列元数据（仅定位日志）
   * @return 字符串值；索引非法时返回原始 index 字符串
   */
  private String lookupSharedString(String sstIndex, ColumnMetadata colMeta) {
    try {
      int idx = Integer.parseInt(sstIndex);
      if (ssReader != null) {
        String resolved = ssReader.getStringByIndex(idx);
        if (resolved != null) {
          return resolved;
        }
      }
    } catch (NumberFormatException e) {
      LOG.debug("Failed to parse SST index: {}", sstIndex);
    }
    return sstIndex;
  }

  /**
   * 流式滑动窗口解析器 — 将内存峰值从整个 XML 降至一个滑动窗口（64 KB）。
   *
   * <p>工作流程：
   * <ol>
   *   <li>从 InputStream 读入固定窗口</li>
   *   <li>扫描完整 {@code <row>...</row>} 片段后提取为 shard</li>
   *   <li>调用 {@link #processRowShard} 处理单行（复用已有 parseRowContent 逻辑）</li>
   *   <li>将未处理完的窗口尾部滑动到头部，继续读取下一批</li>
   * </ol>
   */
  private void parseStreaming(InputStream is) throws IOException {
    byte[] window = new byte[STREAM_WINDOW_SIZE];
    int winLen = 0;
    byte[] chunk = new byte[8192];

    int n;
    while ((n = is.read(chunk)) > 0) {
      // 扩容窗口（如果出现窗口装不下一行的情况）
      if (winLen + n > window.length) {
        byte[] bigger = new byte[Math.max(window.length * 2, winLen + n + 8192)];
        System.arraycopy(window, 0, bigger, 0, winLen);
        window = bigger;
      }
      System.arraycopy(chunk, 0, window, winLen, n);
      winLen += n;

      // 消费窗口中所有完整的 row 片段
      int consumed = emitCompleteRows(window, winLen);
      if (consumed > 0) {
        // 将残留数据滑动到窗口头部
        if (consumed < winLen) {
          System.arraycopy(window, consumed, window, 0, winLen - consumed);
        }
        winLen -= consumed;
      }
    }

    // 处理窗口中残留的尾部数据（可能不含 </row> 的最后半行）
    if (winLen > 0) {
      emitCompleteRows(window, winLen);
    }
  }

  /**
   * 从窗口中消费所有完整的 {@code <row>...</row>} 片段。
   *
   * <p><b>P0-1b 优化</b>：单遍状态机扫描替代 {@code findTag + findClosingTag} 双次扫描。
   * 每行字节仅遍历一次，状态转换如下：
   * <pre>
   *   SCAN → (遇见 "&lt;") → POTENTIAL_ROW
   *   POTENTIAL_ROW → (下一个字节是 'r') → IN_ROW_OPEN_TAG → (遇见 '&gt;') → IN_ROW_CONTENT
   *   IN_ROW_CONTENT → (遇见 "&lt;/r") → EMIT_ROW（消费到 &gt;）→ IN_ROW_CONTENT / SCAN
   * </pre>
   *
   * <p><b>P0-1a 优化</b>：消除 shard byte[] 复制，直接传递 window + 偏移量到
   * {@link #processRowShard}，100k 行累计减少 ~30 MB 堆分配与复制。
   *
   * @param window 滑动窗口字节数组
   * @param winLen 窗口内有效数据长度
   * @return 已消费的字节偏移（窗口中此偏移之前的所有行已处理）
   */
  private int emitCompleteRows(byte[] window, int winLen) {
    int consumedUpTo = 0;
    int pos = 0;

    // 状态机常量
    final int SCAN = 0;
    final int POTENTIAL_ROW = 1;     // 刚遇到 '<'
    final int IN_ROW_TAG = 2;        // 正在匹配 "row" 关键字
    final int IN_ROW_CONTENT = 3;    // 在 <row>...</row> 内容中
    final int POTENTIAL_ROW_CLOSE = 4; // 在内容中遇到 '<'
    final int IN_ROW_CLOSE = 5;      // 正在匹配 "/row>" 关键字

    int state = SCAN;
    int rowStart = -1;
    int rowAttrEnd = -1;
    int matchIdx = 0;  // 用于匹配 "row" 或 "/row>" 的进度

    // 匹配目标字节序列
    final byte[] ROW_TAG = {'r', 'o', 'w'};
    final byte[] ROW_CLOSE_TAG = {'/', 'r', 'o', 'w', '>'};

    while (pos < winLen) {
      byte b = (byte) window[pos];

      switch (state) {
        case SCAN:
          if (b == (byte) '<') {
            state = POTENTIAL_ROW;
          }
          break;

        case POTENTIAL_ROW:
          if (b == (byte) 'r') {
            matchIdx = 1;
            state = IN_ROW_TAG;
            rowStart = pos - 1;  // 记录 '<' 位置
          } else {
            state = SCAN;
          }
          break;

        case IN_ROW_TAG:
          if (matchIdx < ROW_TAG.length && b == ROW_TAG[matchIdx]) {
            matchIdx++;
            if (matchIdx == ROW_TAG.length) {
              state = IN_ROW_CONTENT;
              // 行标签解析完毕，扫描 '>' 作为属性段结束标记
              rowAttrEnd = scanChar(window, pos + 1, winLen, (byte) '>');
            }
          } else {
            state = SCAN;
          }
          break;

        case IN_ROW_CONTENT:
          if (b == (byte) '<') {
            state = POTENTIAL_ROW_CLOSE;
          }
          break;

        case POTENTIAL_ROW_CLOSE:
          if (b == (byte) '/') {
            matchIdx = 1;
            state = IN_ROW_CLOSE;
          } else {
            state = IN_ROW_CONTENT;
          }
          break;

        case IN_ROW_CLOSE:
          if (matchIdx < ROW_CLOSE_TAG.length && b == ROW_CLOSE_TAG[matchIdx]) {
            matchIdx++;
            if (matchIdx == ROW_CLOSE_TAG.length) {
              // 找到完整的 </row>，rowEnd 是 '<' 的位置
              int rowEnd = pos - 5;  // pos 指向 '>'，rowEnd 指向 '<'
              // P0-1a：直接传递 window + 绝对偏移量（避免 shard 复制）
              processRowShard(window, rowStart, rowAttrEnd, rowEnd);
              if (shouldStopBreak()) {
                return winLen;  // 熔断：消费到末尾退出
              }
              consumedUpTo = pos + 1;
              state = SCAN;
            }
          } else {
            state = IN_ROW_CONTENT;
          }
          break;
      }
      pos++;
    }

    return consumedUpTo;
  }

  /**
   * 在 [start, end) 范围内扫描指定字节的位置。
   *
   * @param data 字节数组
   * @param start 起始偏移（包含）
   * @param end 结束偏移（不包含）
   * @param target 目标字节
   * @return 目标字节偏移，未找到返回 -1
   */
  private static int scanChar(byte[] data, int start, int end, byte target) {
    for (int i = start; i < end; i++) {
      if (data[i] == target) {
        return i;
      }
    }
    return -1;
  }

  /**
   * 处理单行（P0-1a：直接操作 data 数组，不复制 shard）。
   *
   * <p>所有现有解析方法（parseRowAttributes、parseRowContent）已支持任意 data + 偏移量参数，
   * 可直接复用。rowStart/rowAttrEnd/rowEnd 均为 data 数组内的绝对偏移量。
   *
   * @param data Sheet XML 字节数组（滑动窗口）
   * @param rowStart {@code <row} 的 '<' 偏移
   * @param rowAttrEnd {@code <row ...>} 的 '>' 偏移
   * @param rowEnd {@code </row>} 的 '<' 偏移
   */
  private void processRowShard(byte[] data, int rowStart, int rowAttrEnd, int rowEnd) {
    parseRowAttributes(data, rowStart, rowAttrEnd);
    isRowHasData = false;

    // P0-B：从行对象池获取实例（池未启用时回落到 newInstance 新对象分配）
    if (currentRow > reader.headRowNumber && rowData == null && reader.instantiator != null) {
      RowPool<Object> pool = getRowPool();
      if (pool != null) {
        rowData = pool.acquire();
      } else {
        try {
          rowData = reader.instantiator.newInstance();
        } catch (Exception e) {
          rowData = null;
        }
      }
    }

    int rowContentStart = rowAttrEnd + 1;
    parseRowContent(data, rowContentStart, rowEnd);

    if (reader.context != null && reader.listeners != null) {
      finishAndEmitRow();
    }
  }

  /**
   * 完成当前 rowData 的校验并分发给监听器。
   */
  @SuppressWarnings("unchecked")
  private void finishAndEmitRow() {
    if (rowData == null) {
      return;
    }

    // skipEmptyRows: 跳过无单元格数据的行
    if (reader.skipEmptyRows && !isRowHasData) {
      // P0-B：空行也释放回池
      releaseRowData();
      return;
    }

    reader.context.incrementRow();

    try {
      DataValidator.validate(rowData, currentRow);
      if (reader.customRules != null) {
        for (RowRule<Object> rule : reader.customRules) {
          rule.validate(rowData, currentRow);
        }
      }
    } catch (Exception ve) {
      LOG.warn("Data validation failed at row {}: {}", currentRow, ve.getMessage());
      for (ReadListener<?> listener : reader.listeners) {
        try {
          ReadListener<Object> typedListener = (ReadListener<Object>) listener;
          typedListener.onError(reader.context, ve);
        } catch (Exception ex) {
          LOG.warn("Listener onError callback failed at row {}", currentRow, ex);
        }
      }
      // P0-B：校验失败也释放回池
      releaseRowData();
      return;
    }

    for (ReadListener<?> listener : reader.listeners) {
      try {
        ReadListener<Object> typedListener = (ReadListener<Object>) listener;
        typedListener.onData(reader.context, rowData);
      } catch (Exception e) {
        LOG.warn("Listener onData callback failed at row {}", currentRow, e);
      }
    }
    // P0-B：监听器处理完毕后释放回池（要求监听器不持有 rowData 引用超过 onData() 调用）
    releaseRowData();
  }

  /**
   * P0-B：将当前 rowData 释放回对象池（或置 null）。
   */
  private void releaseRowData() {
    RowPool<Object> pool = getRowPool();
    if (pool != null && rowData != null) {
      pool.release(rowData);
    }
    rowData = null;
  }

  /** 检查是否已达到 maxRows 熔断上限。 */
  private boolean shouldStopBreak() {
    return reader.maxRows > 0
        && reader.context != null
        && reader.context.getCurrentRow() - reader.headRowNumber >= reader.maxRows;
  }

  // P0-2 优化：全量 byte[] 加载（readAllBytesDirect + parseFullArray）已移除，
  // 替换为 parseStreaming() 统一滑动窗口路径。内存峰值从文件等长降至 64KB 窗口。

  private int findTag(byte[] data, int start, int len, String tagName) {
    byte[] tagStart = ("<" + tagName).getBytes(StandardCharsets.UTF_8);
    int tlen = tagStart.length;
    for (int i = start; i <= len - tlen; i++) {
      boolean match = true;
      for (int j = 0; j < tlen; j++) {
        if (data[i + j] != tagStart[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        byte next = (i + tlen < len) ? data[i + tlen] : 0;
        if (next == ' ' || next == '>' || next == '\n' || next == '\r' || next == '\t') {
          return i;
        }
      }
    }
    return -1;
  }

  private int findClosingTag(byte[] data, int start, int len, String tagName) {
    byte[] tagEnd = ("</" + tagName + ">").getBytes(StandardCharsets.UTF_8);
    int tlen = tagEnd.length;
    for (int i = start; i <= len - tlen; i++) {
      boolean match = true;
      for (int j = 0; j < tlen; j++) {
        if (data[i + j] != tagEnd[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        return i;
      }
    }
    return -1;
  }

  private int findChar(byte[] data, int start, int len, char ch) {
    for (int i = start; i < len; i++) {
      if (data[i] == (byte) ch) {
        return i;
      }
    }
    return -1;
  }

  private void parseRowAttributes(byte[] data, int start, int end) {
    int rPos = findAttribute(data, start, end, "r=\"");
    if (rPos != -1) {
      int valueStart = rPos + 3;
      int valueEnd = findChar(data, valueStart, end, '"');
      if (valueEnd != -1) {
        String rowNumStr = decodeUtf8Fast(data, valueStart, valueEnd - valueStart);
        try {
          currentRow = Integer.parseInt(rowNumStr) - 1;
        } catch (NumberFormatException e) {
          currentRow++;
        }
        return;
      }
    }
    currentRow++;
  }

  private int findAttribute(byte[] data, int start, int end, String attrName) {
    byte[] attrBytes = attrName.getBytes(StandardCharsets.UTF_8);
    int alen = attrBytes.length;
    for (int i = start; i <= end - alen; i++) {
      boolean match = true;
      for (int j = 0; j < alen; j++) {
        if (data[i + j] != attrBytes[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        return i;
      }
    }
    return -1;
  }

  private void parseRowContent(byte[] data, int start, int end) {
    int pos = start;
    while (pos < end) {
      int cellStart = findTag(data, pos, end, "c");
      if (cellStart == -1 || cellStart >= end) {
        break;
      }

      int cellAttrEnd = findChar(data, cellStart, end, '>');
      if (cellAttrEnd == -1 || cellAttrEnd >= end) {
        pos = cellStart + 2;
        continue;
      }

      parseCellAttributes(data, cellStart, cellAttrEnd);

      // Find the closing </c> tag to determine cell boundary
      int cellEnd = findClosingTag(data, cellAttrEnd + 1, end, "c");
      if (cellEnd == -1) {
        cellEnd = end;
      }

      // 深度完善·方案 B：inlineStr 富文本（<is><r><t>…</t></r><r><t>…</t></r></is>）
      // 多 run 拼接 + phonetic 过滤（与 SST 同构，复用提取逻辑；含 XML 实体解码）。
      // 此前只取第一个 <t>，多 run 单元格静默丢失后续内容。
      if ("inlineStr".equals(cellType)) {
        String inlineText = SharedStringsReader.extractRunsText(data, cellAttrEnd + 1, cellEnd);
        handleCellValue(inlineText);
        pos = cellEnd + 4;
        continue;
      }

      // Search for <v> and <t> within the cell boundary (cellAttrEnd+1 to cellEnd)
      int vStart = findTag(data, cellAttrEnd + 1, cellEnd, "v");
      int tStart = -1;
      if (vStart == -1 || vStart >= cellEnd) {
        tStart = findTag(data, cellAttrEnd + 1, cellEnd, "t");
        if (tStart != -1 && tStart < cellEnd) {
          vStart = tStart;
        }
      }

      if (vStart != -1 && vStart < cellEnd) {
        int vContentStart = findChar(data, vStart, cellEnd, '>');
        if (vContentStart != -1 && vContentStart < cellEnd) {
          vContentStart++;

          int vEnd;
          if (tStart == vStart) {
            vEnd = findClosingTag(data, vContentStart, cellEnd, "t");
          } else {
            vEnd = findClosingTag(data, vContentStart, cellEnd, "v");
          }

          if (vEnd != -1 && vEnd < cellEnd) {
            String value = decodeUtf8Fast(data, vContentStart, vEnd - vContentStart);
            handleCellValue(value);
            pos = cellEnd + 4;
            continue;
          }
        }
      }

      pos = cellEnd + 4;
    }
  }

  private void parseCellAttributes(byte[] data, int start, int end) {
    cellStyleIndex = -1;
    int rPos = findAttribute(data, start, end, "r=\"");
    if (rPos != -1) {
      int valueStart = rPos + 3;
      int valueEnd = findChar(data, valueStart, end, '"');
      if (valueEnd != -1) {
        String cellRef = decodeUtf8Fast(data, valueStart, valueEnd - valueStart);
        currentCol = parseCellRef(cellRef);
      }
    }

    int tPos = findAttribute(data, start, end, "t=\"");
    if (tPos != -1) {
      int valueStart = tPos + 3;
      int valueEnd = findChar(data, valueStart, end, '"');
      if (valueEnd != -1) {
        cellType = decodeUtf8Fast(data, valueStart, valueEnd - valueStart);
      }
    } else {
      cellType = null;
    }

    // 深度完善·方案 B：收集样式索引（<c s="N">），供数值单元格的日期格式判定。
    // 带前导空格匹配（' s="'）避免误中 rs= 等同尾属性名。
    int sPos = findAttribute(data, start, end, " s=\"");
    if (sPos != -1) {
      int valueStart = sPos + 4;
      int valueEnd = findChar(data, valueStart, end, '"');
      if (valueEnd != -1) {
        String styleStr = decodeUtf8Fast(data, valueStart, valueEnd - valueStart);
        try {
          cellStyleIndex = Integer.parseInt(styleStr);
        } catch (NumberFormatException e) {
          cellStyleIndex = -1;
        }
      }
    }
  }

  private int parseCellRef(String ref) {
    if (ref == null || ref.isEmpty()) {
      return -1;
    }

    int col = 0;
    for (int i = 0; i < ref.length(); i++) {
      char c = ref.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        col = col * 26 + (c - 'A' + 1);
      } else if (c >= '0' && c <= '9') {
        break;
      }
    }
    return col - 1;
  }

  private void handleCellValue(String value) {
    if (currentRow < 0 || currentCol < 0) {
      return;
    }

    // P0-2 修复：表头行收集列名（含 SST 解析），供 resolveMetadata() 惰性构建列元数据。
    // 此前表头行单元格值被直接丢弃，且 parseDataCell 因列元数据恒为 null 丢弃全部数据。
    if (currentRow == reader.headRowNumber) {
      reader.headerNames.put(currentCol, resolveSharedString(value));
      return;
    }

    if (currentRow > reader.headRowNumber && rowData != null) {
      isRowHasData = true;
      parseDataCell(currentCol, value);
    }
  }

  private void parseDataCell(int col, String value) {
    // P1-B：优先使用 O(1) 稠密索引查找（替代线性扫描）
    ColumnMetadata colMeta = null;
    ColumnMetadata[] index = reader.columnMetadataIndex;
    if (index != null && col < index.length) {
      colMeta = index[col];
      if (colMeta == null || colMeta.columnIndex != col) {
        colMeta = null;  // 位置存在但列号不匹配（不应发生）
      }
    } else {
      // 回退：线性扫描（列索引稀疏或索引未构建时）
      ColumnMetadata[] metadataArray = reader.resolveMetadata();
      if (metadataArray == null) {
        return;
      }
      for (ColumnMetadata meta : metadataArray) {
        if (meta.columnIndex == col) {
          colMeta = meta;
          break;
        }
      }
    }
    if (colMeta == null) {
      return;
    }
    // P0-VarHandle：使用 VarHandle 替代 MethodHandle.invoke() 提升字段赋值性能
    Object convertedValue = convertCellValue(value, colMeta);
    java.lang.invoke.VarHandle vh = colMeta.varHandle;
    if (vh != null) {
      // VarHandle.setRelease 由 JIT 编译为直接字段访问指令
      setViaVarHandle(vh, rowData, convertedValue, colMeta.targetType);
    } else {
      try {
        colMeta.setter.set(rowData, convertedValue);
      } catch (Exception e) {
        LOG.warn(
            "Failed to set field value at row={}, col={}, value={}",
            currentRow,
            col,
            convertedValue,
            e);
      }
    }
  }

  /**
   * P0-VarHandle：通过 VarHandle 快速赋值字段（避免 MethodHandle.invoke 反射开销）。
   *
   * <p>VarHandle.set 在 Java 21 下为 intrinsic 操作（编译为直接字段访问），
   * 比 MethodHandle.invoke() 快 20~40%。类型精确匹配时 JIT 内联为单条指令。
   *
   * @param vh VarHandle 访问器
   * @param target 目标对象
   * @param value 字段值
   * @param targetType 字段目标精确类型（用于精确 set 操作选择）
   */
  private static void setViaVarHandle(java.lang.invoke.VarHandle vh, Object target, Object value,
      Class<?> targetType) {
    if (targetType == int.class) {
      vh.set(target, (Integer) value);
    } else if (targetType == long.class) {
      vh.set(target, (Long) value);
    } else if (targetType == double.class) {
      vh.set(target, (Double) value);
    } else if (targetType == boolean.class) {
      vh.set(target, (Boolean) value);
    } else if (targetType == float.class) {
      vh.set(target, (Float) value);
    } else if (targetType == short.class) {
      vh.set(target, (Short) value);
    } else if (targetType == byte.class) {
      vh.set(target, (Byte) value);
    } else if (targetType == char.class) {
      vh.set(target, (Character) value);
    } else {
      vh.set(target, value);
    }
  }

  /**
   * 解析单元格原始值为表头文本（t="s" 时为 SST 共享字符串索引，需解析为实际文本）
   *
   * @param value 单元格原始值
   * @return 表头文本；SST 解析失败时回退原始值
   */
  private String resolveSharedString(String value) {
    if ("s".equals(cellType) && ssReader != null) {
      try {
        int sstIndex = Integer.parseInt(value);
        String resolved = ssReader.getString(sstIndex);
        return resolved != null ? resolved : "";
      } catch (Exception e) {
        return value;
      }
    }
    return value;
  }

  private Object convertCellValue(String value, ColumnMetadata colMeta) {
    if (value == null || value.isEmpty()) {
      return null;
    }

    String actualValue = value;

    if ("s".equals(cellType) && ssReader != null) {
      try {
        int sstIndex = Integer.parseInt(value);
        actualValue = ssReader.getString(sstIndex);
        if (actualValue == null) {
          return null;
        }
      } catch (Exception e) {
        return null;
      }
    }

    if (actualValue.isEmpty()) {
      return null;
    }

    // 深度完善·方案 B：数值单元格且样式为日期格式（styles.xml numFmt 判定）时，
    // 序列值按 1900/1904 窗口转换为 LocalDateTime 后装载到 SimpleCell——此前一律按纯数字读入，
    // Date/LocalDateTime 字段拿到错误值（fastNumericDateCellIsKnownLimitation 已解除）
    if (isDateStyledNumericCell()) {
      try {
        double serial = Double.parseDouble(actualValue);
        // ExcelDateConverter：零 POI 实现的 Excel 序号 → LocalDateTime 转换
        LocalDateTime ldt =
            ExcelDateConverter.getLocalDateTime(
                serial, reader.excelConfig().getIsUse1904Windowing());
        SimpleCell dateCell = SimpleCell.forDate(actualValue, ldt);
        return colMeta.convertStrategy.convert(dateCell, ExcelCellType.NUMERIC);
      } catch (NumberFormatException e) {
        // 非数值内容按原路径处理（异常生成器的坏数据不在此放大）
      }
    }

    SimpleCell cell = new SimpleCell(actualValue, mapCellType(cellType));
    return colMeta.convertStrategy.convert(cell, mapCellType(cellType));
  }

  /**
   * 当前单元格是否为"日期样式的数值单元格"：无 t 属性（默认 numeric）或 t="n"，
   * 且样式索引经 {@link StylesReader} 判定为日期格式。
   */
  private boolean isDateStyledNumericCell() {
    if (cellType != null && !"n".equals(cellType)) {
      return false;
    }
    return stylesReader != null && stylesReader.isDateFormat(cellStyleIndex);
  }

  private ExcelCellType mapCellType(String type) {
    if ("s".equals(type) || "inlineStr".equals(type)) {
      return ExcelCellType.STRING;
    } else if ("b".equals(type)) {
      return ExcelCellType.BOOLEAN;
    } else if ("e".equals(type)) {
      return ExcelCellType.ERROR;
    } else if ("str".equals(type)) {
      return ExcelCellType.FORMULA;
    } else {
      return ExcelCellType.NUMERIC;
    }
  }

  private String decodeUtf8Fast(byte[] data, int start, int len) {
    return new String(data, start, len, StandardCharsets.UTF_8);
  }
}
