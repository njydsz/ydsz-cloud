package com.njydsz.common.excel.core.reader.sax;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

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
    // P0-2 优化：统一滑动窗口流式解析
    // — 消除 readAllBytesDirect 全量 byte[] 分配（100k 行 5.8MB → 仅 64KB 窗口）
    // — 小文件 (< 32KB)：单次 read 进入窗口，完整消费后退出（I/O 与原路径相同）
    // — 大文件 (≥ 32KB)：逐次 read 入窗，完整行切片后 emit，残留数据滑动到窗首
    parseStreaming(is);
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
   * 从窗口中消费所有完整的 {@code <row>...</row>} 片段，返回消费到的字节数。
   */
  private int emitCompleteRows(byte[] window, int winLen) {
    int consumedUpTo = 0;
    int searchFrom = 0;

    while (searchFrom < winLen) {
      int rowStart = findTag(window, searchFrom, winLen, "row");
      if (rowStart == -1) {
        break;
      }

      int rowAttrEnd = findChar(window, rowStart, winLen, '>');
      if (rowAttrEnd == -1) {
        break;  // row 标签不完整，需要更多数据
      }

      int rowEnd = findClosingTag(window, rowAttrEnd + 1, winLen, "row");
      if (rowEnd == -1) {
        break;  // </row> 未找到，需要更多数据
      }

      // 提取完整的 row 片段长度 — P0-C：从对象池获取分片缓冲区（消除逐行 byte[] 分配）
      int shardLen = rowEnd + 6 - rowStart;
      byte[] shard = SHARD_BUFFER.acquire(shardLen);
      System.arraycopy(window, rowStart, shard, 0, shardLen);

      // 处理单行 shard
      processRowShard(shard, 0, rowAttrEnd - rowStart, rowEnd - rowStart);

      if (shouldStopBreak()) {
        return winLen;  // 熔断：消费到末尾退出
      }

      searchFrom = rowEnd + 6;
      consumedUpTo = searchFrom;
    }

    return consumedUpTo;
  }

  /**
   * 处理单行 shard（一个完整的 {@code <row>...</row>} byte 片段）。
   *
   * <p>shard 是独立的 byte[]，所有现有解析方法（parseRowAttributes、parseRowContent）
   * 均接受 (data, start, end) 边界参数，可直接复用而无需修改。
   *
   * @param shard 完整的 row XML 字节
   * @param rowStart shard 内的 <row 起始偏移
   * @param rowAttrEnd shard 内的 <row ...> 结束偏移
   * @param rowEnd shard 内的 </row> 起始偏移
   */
  private void processRowShard(byte[] shard, int rowStart, int rowAttrEnd, int rowEnd) {
    parseRowAttributes(shard, rowStart, rowAttrEnd);
    isRowHasData = false;

    if (currentRow > reader.headRowNumber && rowData == null && reader.instantiator != null) {
      try {
        rowData = reader.instantiator.newInstance();
      } catch (Exception e) {
        rowData = null;
      }
    }

    int rowContentStart = rowAttrEnd + 1;
    parseRowContent(shard, rowContentStart, rowEnd);

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
      rowData = null;
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
      rowData = null;
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
    Object convertedValue = convertCellValue(value, colMeta);
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
