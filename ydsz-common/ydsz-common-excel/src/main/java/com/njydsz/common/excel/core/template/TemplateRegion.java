package com.njydsz.common.excel.core.template;

/**
 * 模板区域描述符 — 在模板中标记一个行范围作为数据的循环/条件块，对标 poi-tl 的 {@code {{#each}}...{{/each}}} 语义。
 *
 * <p>将模板中的连续多行（如第 5~7 行，通常包含表头间隔、1 行数据行小计行）视为一个"区域模板"；
 * 每个数据项写入时，将该区域整体复制一份（保留源行的列宽、样式、合并单元格），再填充字段值。
 *
 * <h3>行号约定</h3>
 *
 * <ul>
 *   <li>所有行号从 0 开始（POI 内部下标）</li>
 *   <li>{@code startRow} 包含，{@code endRow} 包含</li>
 *   <li>{@code targetStartRow} 为写入目标的首行，原模板区域不被覆盖</li>
 * </ul>
 *
 * <h3>构建方式</h3>
 *
 * <pre>{@code
 * TemplateRegion region = TemplateRegion.builder()
 *     .sourceStartRow(4)
 *     .sourceEndRow(6)
 *     .targetStartRow(4)
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class TemplateRegion {

  /** 模板源区域起始行（POI 0-based，包含） */
  private final int sourceStartRow;

  /** 模板源区域结束行（POI 0-based，包含） */
  private final int sourceEndRow;

  /** 数据写入的起始行（POI 0-based），{@code -1} 表示原地覆盖源区域（不追加） */
  private final int targetStartRow;

  /** 每次迭代跳过的行数（= sourceEndRow - sourceStartRow + 1） */
  private final int rowSpan;

  private TemplateRegion(Builder builder) {
    this.sourceStartRow = builder.sourceStartRow;
    this.sourceEndRow = builder.sourceEndRow;
    this.targetStartRow = builder.targetStartRow;
    this.rowSpan = sourceEndRow - sourceStartRow + 1;
    if (sourceStartRow < 0 || sourceEndRow < sourceStartRow) {
      throw new IllegalArgumentException(
          "Invalid template region: sourceStartRow="
              + sourceStartRow
              + ", sourceEndRow="
              + sourceEndRow);
    }
  }

  public int getSourceStartRow() {
    return sourceStartRow;
  }

  public int getSourceEndRow() {
    return sourceEndRow;
  }

  public int getTargetStartRow() {
    return targetStartRow;
  }

  /** 区域包含的行数 */
  public int getRowSpan() {
    return rowSpan;
  }

  /** 根据迭代下标 i 计算该次写入的目标起始行 */
  public int resolveTargetRow(int index) {
    if (targetStartRow < 0) {
      return sourceStartRow;
    }
    return targetStartRow + (index * rowSpan);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** {@link TemplateRegion} 构建器 */
  public static final class Builder {
    private int sourceStartRow;
    private int sourceEndRow;
    private int targetStartRow = -1;

    private Builder() {}

    public Builder sourceStartRow(int sourceStartRow) {
      this.sourceStartRow = sourceStartRow;
      return this;
    }

    public Builder sourceEndRow(int sourceEndRow) {
      this.sourceEndRow = sourceEndRow;
      return this;
    }

    public Builder targetStartRow(int targetStartRow) {
      this.targetStartRow = targetStartRow;
      return this;
    }

    public TemplateRegion build() {
      return new TemplateRegion(this);
    }
  }

  @Override
  public String toString() {
    return "TemplateRegion{"
        + "source="
        + sourceStartRow
        + "-"
        + sourceEndRow
        + ", targetStart="
        + targetStartRow
        + ", span="
        + rowSpan
        + '}';
  }
}
