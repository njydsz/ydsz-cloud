package com.njydsz.common.excel.core.reader.sax;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.api.validator.DataValidator;
import com.njydsz.common.excel.core.listener.ReadListener;
import com.njydsz.common.excel.core.reader.ColumnMetadata;
import com.njydsz.common.excel.core.reader.SimpleCell;

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
 * @author ydsz-team
 * @since 1.0.0
 * @see SuperFastExcelReader
 * @see SharedStringsReader
 * @see DataValidator
 */
public class SheetXmlReader {

  /** 日志记录器 */
  private static final Logger log = LoggerFactory.getLogger(SheetXmlReader.class);

  /** 共享字符串表读取器，用于解析 t="s" 类型的单元格 */
  private final SharedStringsReader ssReader;

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

  /** 当前行是否有数据（用于空行跳过） */
  private boolean rowHasData;

  /**
   * 构造 Sheet 读取器。
   *
   * @param reader 父级读取器
   * @param ssReader 共享字符串表读取器
   */
  SheetXmlReader(SuperFastExcelReader reader, SharedStringsReader ssReader) {
    this.reader = reader;
    this.ssReader = ssReader;
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
  void parse(InputStream is) throws IOException {
    byte[] data = readAllBytesDirect(is);
    int pos = 0;
    int len = data.length;

    while (pos < len) {
      int rowStart = findTag(data, pos, len, "row");
      if (rowStart == -1) break;

      int rowAttrEnd = findChar(data, rowStart, len, '>');
      if (rowAttrEnd == -1) {
        pos = rowStart + 4;
        continue;
      }

      parseRowAttributes(data, rowStart, rowAttrEnd);
      rowHasData = false;

      if (currentRow > reader.headRowNumber && rowData == null && reader.instantiator != null) {
        try {
          rowData = reader.instantiator.newInstance();
        } catch (Exception e) {
          rowData = null;
        }
      }

      int rowContentStart = rowAttrEnd + 1;
      int rowEnd = findClosingTag(data, rowContentStart, len, "row");
      if (rowEnd == -1) {
        pos = rowContentStart + 1;
        continue;
      }

      parseRowContent(data, rowContentStart, rowEnd);

      if (rowData != null && reader.context != null && reader.listeners != null) {
        // skipEmptyRows: skip rows with no cell data
        if (reader.skipEmptyRows && !rowHasData) {
          rowData = null;
          pos = rowEnd + 6;
          continue;
        }

        reader.context.incrementRow();

        // P0-3: DataValidator integration in SuperFast read path
        try {
          DataValidator.validate(rowData, currentRow);
        } catch (Exception ve) {
          log.warn("Data validation failed at row {}: {}", currentRow, ve.getMessage());
          for (ReadListener<?> listener : reader.listeners) {
            try {
              ReadListener<Object> typedListener = (ReadListener<Object>) listener;
              typedListener.onError(reader.context, ve);
            } catch (Exception ex) {
              log.warn("Listener onError callback failed at row {}", currentRow, ex);
            }
          }
          rowData = null;
          if (reader.maxRows > 0
              && reader.context.getCurrentRow() - reader.headRowNumber >= reader.maxRows) {
            break;
          }
          pos = rowEnd + 6;
          continue;
        }

        List<ReadListener<?>> safeListeners = reader.listeners;
        for (ReadListener<?> listener : safeListeners) {
          try {
            ReadListener<Object> typedListener = (ReadListener<Object>) listener;
            typedListener.onData(reader.context, rowData);
          } catch (Exception e) {
            log.warn("Listener onData callback failed at row {}", currentRow, e);
          }
        }
      }
      rowData = null;

      if (reader.maxRows > 0
          && reader.context != null
          && reader.context.getCurrentRow() - reader.headRowNumber >= reader.maxRows) {
        break;
      }

      pos = rowEnd + 6;
    }
  }

  private byte[] readAllBytesDirect(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
    byte[] buffer = new byte[8192];
    int len;
    while ((len = is.read(buffer)) > 0) {
      baos.write(buffer, 0, len);
    }
    return baos.toByteArray();
  }

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
      if (match) return i;
    }
    return -1;
  }

  private int findChar(byte[] data, int start, int len, char ch) {
    for (int i = start; i < len; i++) {
      if (data[i] == (byte) ch) return i;
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
      if (match) return i;
    }
    return -1;
  }

  private void parseRowContent(byte[] data, int start, int end) {
    int pos = start;
    while (pos < end) {
      int cellStart = findTag(data, pos, end, "c");
      if (cellStart == -1 || cellStart >= end) break;

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
  }

  private int parseCellRef(String ref) {
    if (ref == null || ref.isEmpty()) return -1;

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

    if (currentRow == reader.headRowNumber) {
      return;
    }

    if (currentRow > reader.headRowNumber && rowData != null) {
      rowHasData = true;
      parseDataCell(currentCol, value);
    }
  }

  private void parseDataCell(int col, String value) {
    if (reader.columnMetadataArray == null) {
      return;
    }

    for (ColumnMetadata colMeta : reader.columnMetadataArray) {
      if (colMeta.columnIndex == col) {
        Object convertedValue = convertCellValue(value, colMeta);
        try {
          colMeta.setter.set(rowData, convertedValue);
        } catch (Exception e) {
          log.warn(
              "Failed to set field value at row={}, col={}, value={}",
              currentRow,
              col,
              convertedValue,
              e);
        }
        break;
      }
    }
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
    } else if ("inlineStr".equals(cellType)) {
    } else {
    }

    if (actualValue.isEmpty()) {
      return null;
    }

    SimpleCell cell = new SimpleCell(actualValue, mapCellType(cellType));
    return colMeta.convertStrategy.convert(cell, mapCellType(cellType));
  }

  private CellType mapCellType(String type) {
    if ("s".equals(type) || "inlineStr".equals(type)) {
      return CellType.STRING;
    } else if ("b".equals(type)) {
      return CellType.BOOLEAN;
    } else if ("e".equals(type)) {
      return CellType.ERROR;
    } else if ("str".equals(type)) {
      return CellType.FORMULA;
    } else {
      return CellType.NUMERIC;
    }
  }

  private String decodeUtf8Fast(byte[] data, int start, int len) {
    return new String(data, start, len, StandardCharsets.UTF_8);
  }
}
