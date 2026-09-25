package com.njydsz.common.excel.core.reader.hssf;

/**
 * BIFF8 记录类型常量。
 *
 * <p>定义 Excel 97-2003（BIFF8）格式的标识符。
 *
 * <p>参考：[MS-XLS] Excel Binary File Format (.xls) Structure
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class Biff8Records {

  private Biff8Records() {}

  // ==================== 关键记录类型 ====================

  /** 文件开始（Workbook Globals Substream 的 BOF） */
  public static final int BOF = 0x0809;
  /** 文件结束 */
  public static final int EOF = 0x000A;
  /** Bound Sheet：Sheet 定义 */
  public static final int BOUNDSHEET = 0x0085;
  /** 行定义 */
  public static final int ROW = 0x0208;
  /** 数值单元格 */
  public static final int NUMBER = 0x0203;
  /** 标签单元格（SST 引用） */
  public static final int LABELSST = 0x00FD;
  /** 标签单元格（内联字符串，BIFF8 之前） */
  public static final int LABEL = 0x0204;
  /** 格式化单元格 */
  public static final int RK = 0x027E;
  /** MulRk：多个 RK 记录 */
  public static final int MULRK = 0x00BD;
  /** 公式 */
  public static final int FORMULA = 0x0006;
  /** 共享字符串表 */
  public static final int SST = 0x00FC;
  /** 扩展 SST（大文件） */
  public static final int EXTSST = 0x00FF;
  /** 格式化索引 */
  public static final int FONT = 0x0031;
  /** 格式 */
  public static final int FORMAT = 0x041E;
  /** XF（扩展格式） */
  public static final int XF = 0x00E0;
  /** 代码页 */
  public static final int CODEPAGE = 0x0042;
  /** 日期系统（1900/1904） */
  public static final int DATESYSTEM = 0x0022;
  /** 文件保护 */
  public static final int PROTECT = 0x0012;
  /** 窗口信息 */
  public static final int WINDOW1 = 0x003D;
  /** 列信息 */
  public static final int COLINFO = 0x007D;
  /** 维度 */
  public static final int DIMENSIONS = 0x0200;
  /** 布尔/错误 */
  public static final int BOOLERR = 0x0205;
  /** 备注 */
  public static final int NOTE = 0x001C;
  /** 选择区域 */
  public static final int SELECTION = 0x001D;
  /** 持续记录 */
  public static final int CONTINUE = 0x003C;

  /**
   * BIFF8 记录头。
   */
  public static final class RecordHeader {
    public final int type;
    public final int length;

    public RecordHeader(int type, int length) {
      this.type = type;
      this.length = length;
    }
  }

  /**
   * Sheet 定义。
   */
  public static final class SheetDefinition {
    public final int offset; // BOF offset in Workbook stream
    public final byte hiddenState; // 0=可见, 1=隐藏, 2=深度隐藏
    public final String name;

    public SheetDefinition(int offset, byte hiddenState, String name) {
      this.offset = offset;
      this.hiddenState = hiddenState;
      this.name = name;
    }
  }
}
