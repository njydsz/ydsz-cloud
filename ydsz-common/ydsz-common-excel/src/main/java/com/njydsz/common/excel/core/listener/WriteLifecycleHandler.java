package com.njydsz.common.excel.core.listener;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Excel 写入生命周期回调 SPI。
 *
 * <p>实现本接口并注册到 {@link com.njydsz.common.excel.core.ExcelWriter#registerWriteHandler}，
 * 即可在写入过程的关键节点介入，常用于埋点、审计、动态样式调整、敏感数据脱敏等场景。
 *
 * <p>所有方法均为 {@code default} 空实现，按需覆写即可，无需实现全部钩子。
 *
 * <h3>生命周期顺序</h3>
 *
 * <ol>
 *   <li>{@link #afterWorkbookCreate} — 工作簿初始化完成、首个 Sheet 就绪后
 *   <li>{@link #afterSheetCreate} — 每个 Sheet 创建后（含 newSheet 切 Sheet 场景）
 *   <li>{@link #afterHeaderWrite} — 表头行写入完成后
 *   <li>{@link #afterRowWrite} — 每行数据写入完成后
 *   <li>{@link #afterCellWrite} — 每个单元格写入完成后（高频回调，实现应尽量轻量）
 *   <li>{@link #beforeWorkbookFlush} — 工作簿刷出到输出流之前
 * </ol>
 *
 * <h3>注册示例</h3>
 *
 * <pre>{@code
 * ExcelFacade.write(baos, User.class)
 *     .sheet("用户列表")
 *     .registerWriteHandler(new WriteLifecycleHandler() {
 *         &#64;Override
 *         public void afterCellWrite(Cell cell, Object value, int row, int col) {
 *             if (value instanceof String s && s.contains("password")) {
 *                 cell.setCellValue("***");
 *             }
 *         }
 *     })
 *     .doWrite(dataList);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.common.excel.core.ExcelWriter#registerWriteHandler
 */
public interface WriteLifecycleHandler {

  /**
   * 工作簿与首个 Sheet 就绪后回调。
   *
   * <p>此时工作簿已初始化、目标 Sheet 已创建，可在此处注入全局 Sheet 级别的配置 （如自定义打印设置、文档属性等）。
   *
   * @param workbook 当前工作簿
   * @param sheet 当前 Sheet
   */
  default void afterWorkbookCreate(Workbook workbook, Sheet sheet) {
    // default no-op
  }

  /**
   * 每个 Sheet 创建后回调（包含初次创建与 newSheet 切换场景）。
   *
   * @param sheet 刚创建/切换到的 Sheet
   */
  default void afterSheetCreate(Sheet sheet) {
    // default no-op
  }

  /**
   * 表头行写入完成后回调。
   *
   * @param sheet 当前 Sheet
   * @param headerRow 表头行号（从 0 开始）
   */
  default void afterHeaderWrite(Sheet sheet, int headerRow) {
    // default no-op
  }

  /**
   * 每个数据行写入完成后回调。
   *
   * @param row 刚写入的行
   * @param rowData 绑定的数据对象（可能为 {@code null}）
   * @param rowIndex 行号（从 0 开始，含表头行）
   */
  default void afterRowWrite(Row row, Object rowData, int rowIndex) {
    // default no-op
  }

  /**
   * 每个单元格写入完成后回调。
   *
   * <p>该钩子在每次单元格写入后调用，回调频次极高；实现应避免耗时操作 （如数据库访问、远程调用），仅做轻量级内存内变换。
   *
   * @param cell 刚写入的单元格
   * @param value 写入的原始值（可能为 {@code null}）
   * @param row 单元格所在行号（从 0 开始）
   * @param col 单元格所在列号（从 0 开始）
   */
  default void afterCellWrite(Cell cell, Object value, int row, int col) {
    // default no-op
  }

  /**
   * 工作簿刷出到输出流之前的回调。
   *
   * <p>可在最终刷盘前做尾部校验/修改（如调整共享公式、注入水印标记等）。
   *
   * @param workbook 待刷出的工作簿
   */
  default void beforeWorkbookFlush(Workbook workbook) {
    // default no-op
  }
}
