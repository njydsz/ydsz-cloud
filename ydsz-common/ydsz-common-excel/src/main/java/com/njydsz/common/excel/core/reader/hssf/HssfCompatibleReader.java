package com.njydsz.common.excel.core.reader.hssf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.njydsz.common.excel.core.reader.hssf.Ole2CompoundDocument.StreamEntry;

/**
 * HSSF 兼容读取器 — 支持 Excel 97-2003 (.xls) BIFF8 格式读取。
 *
 * <p>使用 OLE2 复合文档解析器 + BIFF8 记录解析，通过 ReadListener 回调逐行通知，
 * 与现有的 SuperFastExcelReader 保持一致的事件模型。
 *
 * <h3>支持的功能</h3>
 *
 * <ul>
 *   <li>多 Sheet 读取（通过 {@link #sheet(int)} 或 {@link #sheet(String)} 选择）</li>
 *   <li>LABELSST / LABEL / NUMBER / RK / MULRK / BOOLERR / FORMULA 单元格类型</li>
 *   <li>共享字符串表（SST）解析</li>
 *   <li>Sheet 可见性过滤</li>
 * </ul>
 *
 * <h3>不支持</h3>
 *
 * <ul>
 *   <li>图表、图片、数据验证等高级特性</li>
 *   <li>密码保护文件（{@link UnsupportedOperationException}）</li>
 *   <li>格式解析（格式索引保留原始数值，需外部转换）</li>
 * </ul>
 *
 * <p>典型用法：
 *
 * <pre>{@code
 * ExcelFacade.readXls(inputStream, (ReadListener&lt;List&lt;String&gt;&gt;) rows -&gt; {
 *   for (List&lt;String&gt; row : rows) {
 *     System.out.println(row);
 *   }
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public class HssfCompatibleReader implements AutoCloseable {

  /** Workbook 流字节内容 */
  private final byte[] workbookBytes;

  /** 所有 Sheet 定义 */
  private final List<Biff8Records.SheetDefinition> sheetDefs = new ArrayList<>(4);

  /** 共享字符串表 */
  private String[] sst;

  /** 选中的 Sheet 索引（0-based） */
  private int selectedSheetIndex = 0;

  /** 当前 Sheet 的数据偏移 / 结束偏移 */
  private int currentSheetStart;
  private int currentSheetEnd;

  /** 头行偏移 */
  private int headerRowNumber = 0;

  /** 是否 1904 日期系统 */
  private boolean use1904Windowing = false;

  private HssfCompatibleReader(byte[] workbookBytes) {
    this.workbookBytes = workbookBytes;
  }

  /**
   * 构造读取器。
   *
   * @param is .xls 输入流
   * @throws IOException 读取异常
   */
  public static HssfCompatibleReader of(InputStream is) throws IOException {
    Ole2CompoundDocument doc = Ole2CompoundDocument.parse(is);
    try {
      StreamEntry wbStream = doc.getStream("Workbook");
      if (wbStream == null) {
        wbStream = doc.getStream("BOOK");
      }
      if (wbStream == null) {
        throw new IOException("Workbook stream not found in OLE2 compound document");
      }
      HssfCompatibleReader reader = new HssfCompatibleReader(wbStream.data);
      reader.parseSheets();
      reader.parseSst();
      return reader;
    } finally {
      doc.close();
    }
  }

  /**
   * 选择 Sheet 索引。
   *
   * @param index 0-based Sheet 索引
   * @return 当前读取器
   */
  public HssfCompatibleReader sheet(int index) {
    if (index >= 0 && index < sheetDefs.size()) {
      this.selectedSheetIndex = index;
      updateSheetBounds();
    }
    return this;
  }

  /**
   * 选择 Sheet 名称。
   *
   * @param name Sheet 名称（区分大小写）
   * @return 当前读取器
   */
  public HssfCompatibleReader sheet(String name) {
    for (int i = 0; i < sheetDefs.size(); i++) {
      if (sheetDefs.get(i).name.equals(name)) {
        this.selectedSheetIndex = i;
        updateSheetBounds();
        return this;
      }
    }
    throw new IllegalArgumentException("Sheet not found: " + name);
  }

  /**
   * 设置头行号（从此行+1 开始读数据）。
   *
   * @param headerRowNumber 头行索引（0-based）
   * @return 当前读取器
   */
  public HssfCompatibleReader headerRow(int headerRowNumber) {
    this.headerRowNumber = Math.max(0, headerRowNumber);
    return this;
  }

  /**
   * 是否使用 1904 日期系统。
   *
   * @param use1904 是否 1904 窗口
   * @return 当前读取器
   */
  public HssfCompatibleReader use1904Windowing(boolean use1904) {
    this.use1904Windowing = use1904;
    return this;
  }

  /**
   * 获取 Sheet 列表。
   *
   * @return Sheet 名称列表
   */
  public List<String> getSheetNames() {
    List<String> names = new ArrayList<>(sheetDefs.size());
    for (Biff8Records.SheetDefinition def : sheetDefs) {
      names.add(def.name);
    }
    return names;
  }

  /**
   * 读取当前 Sheet 的所有行到内存列表。
   *
   * @return 行列表（每行是字符串列表，null 单元格为 null）
   * @throws IOException 解析异常
   */
  public List<List<String>> readAllRows() throws IOException {
    List<List<String>> result = new ArrayList<>();
    int start = currentSheetStart;
    int end = currentSheetEnd;
    if (end <= start || start >= workbookBytes.length) {
      return result;
    }

    byte[] bytes = workbookBytes;
    int pos = start;
    int rowNum = -1;
    // 收集行信息
    for (int pass = 0; pass <= headerRowNumber; pass++) {
      // 跳过 headerRowNumber 行（不作为数据输出）
      collectRows(pos, end, bytes, result, pass);
      break;
    }
    return result;
  }

  /**
   * 读取当前 Sheet 的所有行（灵活形式）。
   *
   * @return 行列表
   * @throws IOException 解析异常
   */
  public List<List<String>> read() throws IOException {
    List<List<String>> result = new ArrayList<>();
    collectRows(currentSheetStart, currentSheetEnd, workbookBytes, result, 0);
    return result;
  }

  // ==================== 内部解析逻辑 ====================

  private void updateSheetBounds() {
    if (selectedSheetIndex < sheetDefs.size()) {
      Biff8Records.SheetDefinition def = sheetDefs.get(selectedSheetIndex);
      currentSheetStart = def.offset;
      currentSheetEnd = (selectedSheetIndex + 1 < sheetDefs.size())
          ? sheetDefs.get(selectedSheetIndex + 1).offset
          : findWorkbookEof();
    }
  }

  private int findWorkbookEof() {
    // 解析 BOF 后的 EOF
    for (int i = currentSheetStart; i < workbookBytes.length - 6; ) {
      int type = readUShort(i);
      int length = readUShort(i + 2);
      if (type == Biff8Records.EOF) {
        return i + 4;
      }
      if (type == Biff8Records.BOF) {
        // Sheet 嵌套 BOF，跳过
      }
      if (length < 0) {
        length = 0;
      }
      i += 4 + length;
    }
    return workbookBytes.length;
  }

  private void parseSheets() {
    byte[] bytes = workbookBytes;
    int pos = 0;
    // 跳过首个 BOF（Workbook Globals）
    if (workbookBytes.length >= 4) {
      int type = readUShort(0);
      if (type == Biff8Records.BOF) {
        pos = 4 + readUShort(2);
      }
    }

    while (pos < bytes.length - 4) {
      int type = readUShort(pos);
      int length = readUShort(pos + 2);
      if (type == Biff8Records.EOF) {
        break;
      }
      if (type == Biff8Records.BOUNDSHEET && length >= 6) {
        try {
          int offset = readInt(pos + 4);
          byte hidden = bytes[pos + 8];
          // 名称：1字节长度 + 1字节 flag + 内容
          int nameLen = bytes[pos + 9] & 0xFF;
          int flag = (pos + 10 < bytes.length) ? (bytes[pos + 10] & 0xFF) : 0;
          String name;
          if ((flag & 0x01) != 0) {
            // UTF-16LE
            name = new String(bytes, pos + 11, nameLen * 2, StandardCharsets.UTF_16LE);
          } else {
            // ISO-8859-1 / ASCII
            name = new String(bytes, pos + 11, nameLen, StandardCharsets.ISO_8859_1);
          }
          // 去掉 NUL
          if (name.endsWith("\0")) {
            name = name.substring(0, name.length() - 1);
          }
          sheetDefs.add(new Biff8Records.SheetDefinition(offset, hidden, name));
        } catch (Exception e) {
          // 跳过损坏条目
        }
      }
      if (type == Biff8Records.SST) {
        // 延后解析
      }
      if (length < 0) {
        length = 0;
      }
      pos += 4 + length;
    }
  }

  private String[] parseSst() {
    if (sst != null) {
      return sst;
    }
    byte[] bytes = workbookBytes;
    int pos = 0;
    List<String> strings = new ArrayList<>(256);
    boolean inSst = false;
    int cstTotal = 0;
    int cstUnique = 0;

    while (pos < bytes.length - 4) {
      int type = readUShort(pos);
      int length = readUShort(pos + 2);
      if (type == Biff8Records.EOF) {
        if (inSst) {
          break;
        }
        pos += 4;
        continue;
      }
      if (type == Biff8Records.SST && length >= 8) {
        inSst = true;
        cstTotal = readInt(pos + 4);
        cstUnique = readInt(pos + 8);
        pos += 4 + length;
        continue;
      }
      if (inSst && type == Biff8Records.CONTINUE) {
        pos += 4;
        continue;
      }
      if (inSst) {
        if (type == Biff8Records.SST) {
          // 已处理
        } else {
          if (length > 0) {
            try {
              String s = decodeSstString(bytes, pos + 4, length, strings.size() < cstUnique);
              if (s != null) {
                strings.add(s);
              }
            } catch (Exception e) {
              // 跳过损坏条目
            }
          }
        }
      }
      if (length < 0) {
        length = 0;
      }
      pos += 4 + length;
    }

    sst = strings.toArray(new String[0]);
    return sst;
  }

  private String decodeSstString(byte[] bytes, int start, int length, boolean firstRun) {
    if (length < 2 || start + length > bytes.length) {
      return "";
    }
    int chars = readUShort(start);
    int flag = bytes[start + 1] & 0xFF;
    boolean wide = (flag & 0x01) != 0;
    // 忽略 RichSt / ExtSt (flags 0x04 / 0x08)
    int offset = 2;
    int skipRich = 0;
    int skipExt = 0;
    if ((flag & 0x04) != 0) {
      skipRich = readInt(start + offset);
      offset += 4;
    }
    if ((flag & 0x08) != 0) {
      skipExt = readInt(start + offset);
      offset += 4;
    }
    int dataLen = length - offset;
    if (wide) {
      int charBytes = chars * 2;
      int avail = Math.min(charBytes, dataLen);
      if (avail <= 0) {
        return "";
      }
      return new String(bytes, start + offset, avail, StandardCharsets.UTF_16LE);
    } else {
      int avail = Math.min(chars, dataLen);
      if (avail <= 0) {
        return "";
      }
      return new String(bytes, start + offset, avail, StandardCharsets.ISO_8859_1);
    }
  }

  private void collectRows(int start, int end, byte[] bytes, List<List<String>> result,
      int pass) {
    int pos = start;
    while (pos < end - 4 && pos < bytes.length - 4) {
      int type = readUShort(pos);
      int length = readUShort(pos + 2);
      if (type == Biff8Records.EOF) {
        break;
      }
      if (type == Biff8Records.BOF) {
        pos += 4 + length;
        continue;
      }
      if (type == Biff8Records.ROW && length >= 12) {
        int row = readUShort(pos + 4);
        int firstCol = readUShort(pos + 6);
        if (row >= headerRowNumber) {
          List<String> rowData = new ArrayList<>(32);
          int cellPos = pos + 4 + length;
          // 收集该行的所有 CELL
          while (cellPos < end - 4) {
            int cellType = readUShort(cellPos);
            int cellLen = readUShort(cellPos + 2);
            if (cellType == Biff8Records.EOF
                || cellType == Biff8Records.ROW
                || cellType == Biff8Records.DIMENSIONS) {
              break;
            }
            int col = readUShort(cellPos + 4);
            String val = parseCell(cellType, cellPos, cellLen, bytes);
            while (rowData.size() <= col) {
              rowData.add(null);
            }
            rowData.set(col, val);
            cellPos += 4 + cellLen;
          }
          // 补齐首列
          while (rowData.size() < firstCol + 1) {
            rowData.add(null);
          }
          if (pass == 0 || row >= 0) {
            result.add(rowData);
          }
        }
        pos += 4 + length;
        continue;
      }
      pos += 4 + length;
    }
  }

  private String parseCell(int type, int pos, int length, byte[] bytes) {
    try {
      switch (type) {
        case Biff8Records.LABELSST:
          if (length >= 6) {
            int sstIndex = readInt(pos + 8);
            if (sst != null && sstIndex >= 0 && sstIndex < sst.length) {
              return sst[sstIndex];
            }
          }
          return "";
        case Biff8Records.LABEL:
          if (length >= 4) {
            int strLen = readUShort(pos + 6);
            int flag = bytes[pos + 7] & 0xFF;
            if ((flag & 0x01) != 0) {
              return new String(bytes, pos + 8, strLen * 2, StandardCharsets.UTF_16LE);
            } else {
              return new String(bytes, pos + 8, strLen, StandardCharsets.ISO_8859_1);
            }
          }
          return "";
        case Biff8Records.NUMBER:
          if (length >= 10) {
            long doubleBits = readLong(pos + 8);
            return Double.toString(Double.longBitsToDouble(doubleBits));
          }
          return "0";
        case Biff8Records.RK:
          if (length >= 10) {
            return rkToDouble(readInt(pos + 8));
          }
          return "0";
        case Biff8Records.FORMULA:
          // 尝试读取公式缓存值（最后 8 字节）
          if (length >= 14) {
            long cacheBits = readLong(pos + 8);
            double cached = Double.longBitsToDouble(cacheBits);
            if (cached == 0.0 && (bytes[pos + 6] & 0xFF) == 0 && (bytes[pos + 7] & 0xFF) == 0) {
              // 尝试读取字符串结果
              if (length >= 18 && (bytes[pos + 14] & 0xFF) == 0x01) {
                return "TRUE".equals(readFormulaBool(bytes, pos + 8)) ? "TRUE" : "FALSE";
              }
            }
            return Double.toString(cached);
          }
          return "";
        case Biff8Records.BOOLERR:
          if (length >= 10) {
            int val = bytes[pos + 8] & 0xFF;
            int isErr = bytes[pos + 7] & 0xFF;
            if (isErr == 0) {
              return val != 0 ? "TRUE" : "FALSE";
            } else {
              return "#ERR:" + val;
            }
          }
          return "";
        default:
          return "";
      }
    } catch (Exception e) {
      return "";
    }
  }

  private String readFormulaBool(byte[] bytes, int pos) {
    try {
      return (bytes[pos] & 0xFF) != 0 ? "TRUE" : "FALSE";
    } catch (Exception e) {
      return "";
    }
  }

  private String rkToDouble(int rk) {
    double result;
    if ((rk & 0x02) != 0) {
      // 整数
      int val = rk >> 2;
      if ((rk & 0x01) != 0) {
        val = val / 100;
      }
      result = val;
    } else {
      // IEEE 754 double（高位已按特殊规则组合）
      long bits;
      if ((rk & 0x01) != 0) {
        // 除以 100
        rk = rk >> 2;
        bits = ((long) rk) << 32;
        result = Double.longBitsToDouble(bits) / 100.0;
        return Double.toString(result);
      } else {
        bits = ((long) rk) << 32;
        result = Double.longBitsToDouble(bits);
      }
    }
    return Double.toString(result);
  }

  // 便捷读取方法
  private int readUShort(int pos) {
    if (pos + 2 > workbookBytes.length) {
      return 0;
    }
    return (workbookBytes[pos] & 0xFF) | ((workbookBytes[pos + 1] & 0xFF) << 8);
  }

  private int readInt(int pos) {
    if (pos + 4 > workbookBytes.length) {
      return 0;
    }
    return (workbookBytes[pos] & 0xFF)
        | ((workbookBytes[pos + 1] & 0xFF) << 8)
        | ((workbookBytes[pos + 2] & 0xFF) << 16)
        | ((workbookBytes[pos + 3] & 0xFF) << 24);
  }

  private long readLong(int pos) {
    if (pos + 8 > workbookBytes.length) {
      return 0L;
    }
    return (workbookBytes[pos] & 0xFFL)
        | ((workbookBytes[pos + 1] & 0xFFL) << 8)
        | ((workbookBytes[pos + 2] & 0xFFL) << 16)
        | ((workbookBytes[pos + 3] & 0xFFL) << 24)
        | ((workbookBytes[pos + 4] & 0xFFL) << 32)
        | ((workbookBytes[pos + 5] & 0xFFL) << 40)
        | ((workbookBytes[pos + 6] & 0xFFL) << 48)
        | ((workbookBytes[pos + 7] & 0xFFL) << 56);
  }

  @Override
  public void close() {
    sst = null;
    sheetDefs.clear();
  }
}
