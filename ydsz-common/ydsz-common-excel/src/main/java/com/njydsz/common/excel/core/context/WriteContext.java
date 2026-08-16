package com.njydsz.common.excel.core.context;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.njydsz.common.excel.core.ExcelWriter;
import com.njydsz.common.excel.core.metadata.WriteMetadata;

/**
 * 写入上下文 - 写入过程中状态信息的载体
 *
 * <p>记录当前写入的位置信息和元数据,用于追踪写入进度。 主要在写入处理器回调和内部状态管理时使用。
 *
 * @see ExcelWriter
 * @see WriteHandler
 * @author ydsz-team
 * @since 1.0.0
 */
public class WriteContext {

  /** 写入元数据 */
  private WriteMetadata metadata;

  /** 当前写入的工作簿 */
  private Workbook workbook;

  /** 当前写入的Sheet */
  private Sheet sheet;

  /** 当前写入的行号 */
  private int currentRow;

  /** 当前写入的列号 */
  private int currentColumn;

  /** 是否已完成写入 */
  private boolean finished;

  public WriteContext() {}

  public WriteContext(WriteMetadata metadata) {
    this.metadata = metadata;
    this.currentRow = metadata.getHeadRowNumber();
  }

  public WriteMetadata getMetadata() {
    return metadata;
  }

  public void setMetadata(WriteMetadata metadata) {
    this.metadata = metadata;
  }

  public Workbook getWorkbook() {
    return workbook;
  }

  public void setWorkbook(Workbook workbook) {
    this.workbook = workbook;
  }

  public Sheet getSheet() {
    return sheet;
  }

  public void setSheet(Sheet sheet) {
    this.sheet = sheet;
  }

  public int getCurrentRow() {
    return currentRow;
  }

  public void setCurrentRow(int currentRow) {
    this.currentRow = currentRow;
  }

  public int getCurrentColumn() {
    return currentColumn;
  }

  public void setCurrentColumn(int currentColumn) {
    this.currentColumn = currentColumn;
  }

  public boolean isFinished() {
    return finished;
  }

  public void setFinished(boolean finished) {
    this.finished = finished;
  }

  /**
   * 将当前写入行号前进一行。
   *
   * <p>注意行游标的<b>起点是表头行数</b>（见带参构造），即首次写入的数据行紧接表头之后； 本方法只推进行号，不会自动重置列号，换行时通常需配合 {@link
   * #resetColumn()} 使用。
   *
   * <p><b>线程安全性</b>：非原子操作，写入上下文假定单线程串行写入。
   */
  public void incrementRow() {
    this.currentRow++;
  }

  /**
   * 将当前写入列号前进一列。
   *
   * <p>由写入流程在每写完一个单元格后调用；不做列数上界校验， 超出 Excel 单表最大列数时由底层 POI 抛出异常。
   */
  public void incrementColumn() {
    this.currentColumn++;
  }

  /**
   * 将列游标复位到行首（第 0 列）。
   *
   * <p>每次换行前必须调用，否则列号会跨行持续累加导致数据写偏。
   */
  public void resetColumn() {
    this.currentColumn = 0;
  }
}
