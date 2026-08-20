package com.njydsz.common.excel.core.reader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Excel XML 手工解析器。
 *
 * <p>直接操作 byte[] 解析 Excel .xlsx 文件中的 sheet XML， 绕过 Apache POI 的 DOM/SAX 解析器，以极低的内存开销实现高性能读取。
 *
 * <h3>解析原理</h3>
 *
 * <p>Excel .xlsx 文件中的 sheet XML 结构为：
 *
 * <pre>{@code
 * <row r="1">
 *   <c r="A1" t="s" s="0">
 *     <v>0</v>
 *   </c>
 * </row>
 * }</pre>
 *
 * 本解析器通过逐字节扫描 XML 标签前缀（如 {@code <row}, {@code <c}, {@code <v>}）， 定位行、单元格、值的位置，然后提取属性和文本内容。
 *
 * <h3>回调接口</h3>
 *
 * <ul>
 *   <li>{@link RowHandler}：行开始/结束回调
 *   <li>{@link CellHandler}：单元格开始/值/结束回调
 * </ul>
 *
 * <h3>支持的单元格类型</h3>
 *
 * <ul>
 *   <li>{@code s}：共享字符串（回调返回的是 SST 索引，需调用方查表还原）
 *   <li>{@code inlineStr}：内联字符串
 *   <li>{@code str}：公式结果字符串
 *   <li>{@code n}：数值
 *   <li>{@code b}：布尔值
 *   <li>{@code e}：错误值
 * </ul>
 *
 * <h3>注意事项</h3>
 *
 * <ul>
 *   <li><b>线程安全性</b>：解析游标（{@code pos}、{@code currentRow} 等）为实例字段， {@link #parse}
 *       每次调用都会重置它们，因此实例<b>不可</b>在多线程间共享， 也不支持同一实例并发解析多份数据。
 *   <li>解析过程对畸形 XML 保持宽容：找不到闭合标签时跳过该片段而非抛异常， 以保证部分损坏的文件仍能读出可用数据。
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ExcelXmlParser {

  private static final Logger LOG = LoggerFactory.getLogger(ExcelXmlParser.class);

  /** XML 标签前缀：<row */
  private static final byte[] ROW_START = "<row".getBytes(StandardCharsets.UTF_8);

  /** XML 标签：</row> */
  private static final byte[] ROW_END = "</row>".getBytes(StandardCharsets.UTF_8);

  /** XML 标签前缀：<c */
  private static final byte[] CELL_START = "<c".getBytes(StandardCharsets.UTF_8);

  /** XML 标签：<v>（单元格值） */
  private static final byte[] VALUE_START = "<v>".getBytes(StandardCharsets.UTF_8);

  /** XML 标签：</v> */
  private static final byte[] VALUE_END = "</v>".getBytes(StandardCharsets.UTF_8);

  /** XML 标签：<is>（内联字符串） */
  private static final byte[] IS_TAG = "<is>".getBytes(StandardCharsets.UTF_8);

  /** XML 标签：<t>（文本） */
  private static final byte[] T_TAG = "<t>".getBytes(StandardCharsets.UTF_8);

  /** XML 标签：</t> */
  private static final byte[] T_CLOSE = "</t>".getBytes(StandardCharsets.UTF_8);

  /** XML 标签：<f>（公式） */
  private static final byte[] FORMULA_START = "<f>".getBytes(StandardCharsets.UTF_8);

  /** XML 标签：</f> */
  private static final byte[] FORMULA_END = "</f>".getBytes(StandardCharsets.UTF_8);

  /** XML 属性名：r="（单元格引用，如 A1） */
  private static final byte[] ATTR_R = " r=\"".getBytes(StandardCharsets.UTF_8);

  /** XML 属性名：t="（单元格类型） */
  private static final byte[] ATTR_T = " t=\"".getBytes(StandardCharsets.UTF_8);

  /** XML 属性名：s="（单元格样式索引） */
  private static final byte[] ATTR_S = " s=\"".getBytes(StandardCharsets.UTF_8);

  /** 当前解析位置 */
  private int pos;

  /** 数据长度限制 */
  private int limit;

  /** 当前行号 */
  private int currentRow;

  /** 当前列号（0-based） */
  private int currentCol;

  /** 当前单元格引用（如 "A1"） */
  private String cellRef;

  /** 当前单元格类型（s/n/b/e/inlineStr/str） */
  private String cellType;

  /** 当前单元格样式索引 */
  private int cellStyle;

  /** 行回调处理器 */
  private RowHandler rowHandler;

  /** 单元格回调处理器 */
  private CellHandler cellHandler;

  /**
   * 行回调接口。
   *
   * <p>在解析到 {@code <row>} 开始和 {@code </row>} 结束时触发。
   */
  public interface RowHandler {
    /** 行开始时回调 */
    void onRowStart(int rowNum);

    /** 行结束时回调 */
    void onRowEnd(int rowNum);
  }

  /**
   * 单元格回调接口。
   *
   * <p>在解析到 {@code <c>} 标签、{@code <v>} 值标签和 {@code </c>} 结束标签时触发。
   */
  public interface CellHandler {
    /**
     * 单元格开始时回调。
     *
     * @param row 行号（1-based）
     * @param col 列号（0-based）
     * @param ref 单元格引用（如 "A1"）
     * @param type 单元格类型（s/n/b/e/inlineStr/str）
     * @param style 样式索引
     */
    void onCellStart(int row, int col, String ref, String type, int style);

    /**
     * 单元格值回调。
     *
     * @param row 行号
     * @param col 列号
     * @param value 值字符串（数值/布尔/字符串/公式结果）
     */
    void onCellValue(int row, int col, String value);

    /** 单元格结束时回调 */
    void onCellEnd(int row, int col);
  }

  /**
   * 构造解析器。
   *
   * @param bufferSize 缓冲区大小（当前实现为全量读取，此参数保留兼容）
   */
  public ExcelXmlParser(int bufferSize) {}

  /**
   * 解析 Excel sheet XML 字节数据。
   *
   * <p>主解析循环：逐字节扫描 XML，匹配标签前缀后分发到对应的解析方法。 解析顺序：{@code <row>} → {@code <c>} → {@code <v>} / {@code
   * <is>} → 值回调。
   *
   * @param data XML 字节数据
   * @param rowHandler 行回调处理器
   * @param cellHandler 单元格回调处理器
   */
  public void parse(byte[] data, RowHandler rowHandler, CellHandler cellHandler) {
    this.rowHandler = rowHandler;
    this.cellHandler = cellHandler;
    this.pos = 0;
    this.limit = data.length;
    this.currentRow = -1;
    this.currentCol = -1;

    while (pos < limit) {
      if (data[pos] == '<') {
        if (matchTag(data, ROW_START)) {
          parseRowTag(data);
        } else if (matchTag(data, CELL_START)) {
          parseCellTag(data);
        } else if (matchTag(data, VALUE_START)) {
          parseValueTag(data);
        } else {
          skipTag(data);
        }
      } else {
        pos++;
      }
    }
  }

  /**
   * 检查当前位置是否匹配指定标签。
   *
   * @param data 原始字节数据
   * @param tag 目标标签字节数组
   * @return 匹配返回 true
   */
  private boolean matchTag(byte[] data, byte[] tag) {
    if (pos + tag.length > limit) {
      return false;
    }
    for (int i = 0; i < tag.length; i++) {
      if (data[pos + i] != tag[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * 解析 {@code <row>} 标签，提取行号并触发行开始/结束回调。
   *
   * @param data XML 字节数据
   */
  private void parseRowTag(byte[] data) {
    int tagEnd = findChar(data, '>', pos);
    if (tagEnd == -1) {
      skipTag(data);
      return;
    }

    int rowNum = parseRowNumber(data, pos);
    if (rowNum > 0) {
      currentRow = rowNum;
      if (rowHandler != null) {
        rowHandler.onRowStart(currentRow);
      }
    }

    pos = tagEnd + 1;

    while (pos < limit && data[pos] != '<') {
      if (data[pos] == '<' && matchTag(data, ROW_END)) {
        if (rowHandler != null && currentRow > 0) {
          rowHandler.onRowEnd(currentRow);
        }
        pos += ROW_END.length;
        currentRow = -1;
        currentCol = -1;
        return;
      }
      pos++;
    }
  }

  /**
   * 从 {@code <row r="N">} 标签中解析行号。
   *
   * @param data 原始字节数据
   * @param start 标签起始位置
   * @return 行号（1-based），解析失败返回 -1
   */
  private int parseRowNumber(byte[] data, int start) {
    int pos = start + 4;
    while (pos < limit && data[pos] != 'r') {
      pos++;
    }
    if (pos >= limit || data[pos] != 'r') {
      return -1;
    }

    int eqPos = pos + 1;
    while (eqPos < limit && data[eqPos] != '=' && data[eqPos] != '"') {
      eqPos++;
    }
    while (eqPos < limit && data[eqPos] != '"') {
      eqPos++;
    }
    int valueStart = eqPos + 1;
    int valueEnd = valueStart;
    while (valueEnd < limit && data[valueEnd] != '"') {
      valueEnd++;
    }

    try {
      String numStr = new String(data, valueStart, valueEnd - valueStart, StandardCharsets.UTF_8);
      return Integer.parseInt(numStr);
    } catch (Exception e) {
      return -1;
    }
  }

  /**
   * 解析 {@code <c>} 单元格标签。
   *
   * <p>提取单元格引用（r）、类型（t）、样式（s）属性， 然后根据类型从 {@code <v>} 或 {@code <is>/<t>} 中提取值。
   *
   * @param data XML 字节数据
   */
  private void parseCellTag(byte[] data) {
    int tagEnd = findChar(data, '>', pos);
    if (tagEnd == -1) {
      skipTag(data);
      return;
    }

    cellRef = parseAttribute(data, ATTR_R);
    currentCol = parseCellRef(cellRef);

    cellType = parseAttribute(data, ATTR_T);
    if (cellType == null) {
      cellType = "";
    }

    String styleStr = parseAttribute(data, ATTR_S);
    cellStyle = 0;
    if (styleStr != null && !styleStr.isEmpty()) {
      try {
        cellStyle = Integer.parseInt(styleStr);
      } catch (NumberFormatException e) {
        LOG.debug("Caught exception (ignored): {}", e.getMessage());
      }
    }

    if (cellHandler != null && currentRow > 0 && currentCol >= 0) {
      cellHandler.onCellStart(currentRow, currentCol, cellRef, cellType, cellStyle);
    }

    if (cellType.equals("inlineStr") || cellType.equals("str")) {
      int isPos = findBytes(data, IS_TAG, pos, tagEnd);
      if (isPos == -1) {
        isPos = findBytes(data, T_TAG, pos, tagEnd);
      }
      if (isPos != -1) {
        int tStart = isPos + IS_TAG.length;
        int tEnd = findBytes(data, T_CLOSE, tStart, tagEnd);
        if (tEnd == -1) {
          tEnd = findBytes(data, "</t>", tStart, tagEnd);
        }
        if (tEnd != -1) {
          String value = new String(data, tStart, tEnd - tStart, StandardCharsets.UTF_8);
          if (cellHandler != null && currentRow > 0 && currentCol >= 0) {
            cellHandler.onCellValue(currentRow, currentCol, value);
          }
        }
      }
    } else if (cellType.equals("e")
        || cellType.equals("b")
        || cellType.equals("n")
        || cellType.equals("s")
        || cellType.equals("str")
        || cellType.isEmpty()) {
      int vStart = findBytes(data, VALUE_START, pos, tagEnd);
      if (vStart == -1) {
        vStart = findBytes(data, FORMULA_START, pos, tagEnd);
      }
      if (vStart != -1) {
        int valueStart = vStart + VALUE_START.length;
        int valueEnd = findBytes(data, VALUE_END, valueStart, tagEnd);
        int formulaEnd = findBytes(data, FORMULA_END, valueStart, tagEnd);
        if (valueEnd == -1 && formulaEnd != -1) {
          valueEnd = formulaEnd;
        }
        if (valueEnd != -1) {
          String value =
              new String(data, valueStart, valueEnd - valueStart, StandardCharsets.UTF_8);
          if (cellHandler != null && currentRow > 0 && currentCol >= 0) {
            cellHandler.onCellValue(currentRow, currentCol, value);
          }
        }
      }
    }

    pos = tagEnd + 1;
  }

  /**
   * 解析独立的 {@code <v>} 值标签（不在 {@code <c>} 内部时）。
   *
   * @param data XML 字节数据
   */
  private void parseValueTag(byte[] data) {
    int valueStart = pos + VALUE_START.length;
    int valueEnd = findBytes(data, VALUE_END, valueStart, limit);
    if (valueEnd == -1) {
      valueEnd = findBytes(data, "</v>", valueStart, limit);
    }
    if (valueEnd != -1) {
      String value = new String(data, valueStart, valueEnd - valueStart, StandardCharsets.UTF_8);
      if (cellHandler != null && currentRow > 0 && currentCol >= 0) {
        cellHandler.onCellValue(currentRow, currentCol, value);
      }
    }
    pos = valueEnd > 0 ? valueEnd + VALUE_END.length : pos + 1;
  }

  /**
   * 从当前位置解析指定属性的值。
   *
   * <p>属性格式为 {@code name="value"}，此方法从 pos 位置开始搜索属性名， 然后提取引号内的值。
   *
   * @param data 原始字节数据
   * @param attrName 属性名（含等号和引号前缀，如 {@code r="}）
   * @return 属性值字符串，未找到返回 null
   */
  private String parseAttribute(byte[] data, byte[] attrName) {
    int attrPos = findBytes(data, attrName, pos, limit);
    if (attrPos == -1) {
      return null;
    }

    int valueStart = attrPos + attrName.length;
    int valueEnd = valueStart;
    while (valueEnd < limit && data[valueEnd] != '"') {
      valueEnd++;
    }

    if (valueEnd > valueStart) {
      return new String(data, valueStart, valueEnd - valueStart, StandardCharsets.UTF_8);
    }
    return "";
  }

  /**
   * 从单元格引用（如 "A1"、"B23"）中解析列号。
   *
   * <p>列号转换：A=0, B=1, ..., Z=25, AA=26, ...
   *
   * @param ref 单元格引用字符串
   * @return 列号（0-based），解析失败返回 -1
   */
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

  /**
   * 从指定位置开始查找字符。
   *
   * @param data 原始字节数据
   * @param c 目标字符
   * @param start 起始位置
   * @return 字符位置，未找到返回 -1
   */
  private int findChar(byte[] data, char c, int start) {
    for (int i = start; i < limit && i < data.length; i++) {
      if (data[i] == c) {
        return i;
      }
    }
    return -1;
  }

  /**
   * 在字节数组中查找目标字节序列。
   *
   * @param data 原始字节数据
   * @param target 目标字节序列
   * @param start 搜索起始位置
   * @param end 搜索结束位置（不含）
   * @return 匹配位置，未找到返回 -1
   */
  private int findBytes(byte[] data, byte[] target, int start, int end) {
    if (end > data.length) {
      end = data.length;
    }
    outer:
    for (int i = start; i <= end - target.length; i++) {
      for (int j = 0; j < target.length; j++) {
        if (data[i + j] != target[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

  /**
   * 在字节数组中查找目标字符串（UTF-8 编码）。
   *
   * @param data 原始字节数据
   * @param target 目标字符串
   * @param start 搜索起始位置
   * @param end 搜索结束位置（不含）
   * @return 匹配位置，未找到返回 -1
   */
  private int findBytes(byte[] data, String target, int start, int end) {
    byte[] targetBytes = target.getBytes(StandardCharsets.UTF_8);
    return findBytes(data, targetBytes, start, end);
  }

  private void skipTag(byte[] data) {
    int depth = 1;
    while (pos < limit && depth > 0) {
      if (data[pos] == '<') {
        if (pos + 1 < limit) {
          if (data[pos + 1] == '/') {
            depth--;
            if (depth == 0) {
              int endTag = findChar(data, '>', pos);
              if (endTag != -1) {
                pos = endTag + 1;
              } else {
                pos = limit;
              }
              return;
            }
          } else if (data[pos + 1] == '!') {
          } else if (data[pos + 1] == '?') {
          } else {
            boolean isEndTag = false;
            for (int i = 1; i < 5; i++) {
              if (pos + i >= limit) {
                break;
              }
              if (data[pos + i] == '/') {
                isEndTag = true;
                break;
              }
              if (data[pos + i] == ' ' || data[pos + i] == '>') {
                break;
              }
            }
            if (!isEndTag) {
              depth++;
            }
          }
        }
      }
      pos++;
      if (pos >= limit) {
        break;
      }
    }
    int endTag = findChar(data, '>', pos);
    if (endTag != -1) {
      pos = endTag + 1;
    }
  }

  /**
   * 一次性解析出全部非空单元格，供无需流式处理的小数据量场景使用。
   *
   * <p>内部新建独立的解析器实例并注册匿名回调，因此本方法是线程安全的。 仅在 {@code onCellValue} 回调中收集数据，故<b>没有值的单元格不会出现在结果中</b>，
   * 调用方不能依赖下标连续性，必须使用 {@link ParsedCell#row} / {@link ParsedCell#col} 定位。
   *
   * <p><b>内存特征</b>：结果全量驻留堆内，大表请改用 {@link #parse(byte[], RowHandler, CellHandler)} 流式处理。
   *
   * @param sheetData sheet XML 字节数据
   * @param sstTable 共享字符串表；<b>当前实现并未使用该参数</b>， {@code t="s"} 类型单元格返回的仍是 SST 索引字符串而非还原后的文本，
   *     需调用方自行查表转换
   * @return 含值的单元格列表，按解析顺序排列；无数据时返回空列表而非 {@code null}
   */
  public static List<ParsedCell> parseCells(byte[] sheetData, ChunkedSSTTable sstTable) {
    List<ParsedCell> cells = new ArrayList<>();
    ExcelXmlParser parser = new ExcelXmlParser(8192);

    parser.parse(
        sheetData,
        null,
        new CellHandler() {
          @Override
          public void onCellStart(int row, int col, String ref, String type, int style) {}

          @Override
          public void onCellValue(int row, int col, String value) {
            ParsedCell cell = new ParsedCell();
            cell.row = row;
            cell.col = col;
            cell.value = value;
            cells.add(cell);
          }

          @Override
          public void onCellEnd(int row, int col) {}
        });

    return cells;
  }

  /**
   * 单个单元格的解析结果。
   *
   * <p>仅承载有值的单元格（无值单元格不会出现在 {@link #parseCells} 的返回列表中）， 因此调用方不能依赖返回列表下标的连续性，必须通过 {@link #row} /
   * {@link #col} 定位。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  public static class ParsedCell {
    /** 行号（1-based） */
    public int row;

    /** 列号（0-based） */
    public int col;

    /** 单元格值字符串；数值、布尔、字符串、公式结果统一按文本返回 */
    public String value;

    /** 单元格类型（s/n/b/e/inlineStr/str）；未识别时可能为 {@code null} */
    public String type;

    @Override
    public String toString() {
      return "ParsedCell{row="
          + row
          + ", col="
          + col
          + ", value='"
          + value
          + "', type='"
          + type
          + "'}";
    }
  }
}
