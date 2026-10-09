package com.njydsz.agent.server.util;

import java.io.OutputStream;
import java.util.List;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.ExcelWriter;

/**
 * Agent 模块 Excel 导出工具类 — 封装流式写入输出流。
 *
 * <p>将 Excel 数据通过 {@link ExcelWriter} 流式写入 {@link OutputStream}，
 * 避免 ByteArrayOutputStream 中间缓冲。HTTP 响应头（Content-Type / Content-Disposition）
 * 由调用方 Controller 设置，本类仅负责数据序列化。
 *
 * <h3>设计意图</h3>
 *
 * <ul>
 *   <li>位于 server 层，无 servlet 依赖，可复用</li>
 *   <li>符合 DDD 分层：server 层通过 OutputStream 抽象提供序列化能力</li>
 *   <li>HTTP 头设置（Content-Type/Content-Disposition）属于 web 层职责，由 Controller 直接持有</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.09
 */
public final class ExcelExportUtil {

  private ExcelExportUtil() {
    // 静态工具类，禁止实例化
  }

  /**
   * 流式写入 Excel 到输出流。
   *
   * <p>数据通过 {@link ExcelWriter} 流式写入 outputStream，
   * 不经过 ByteArrayOutputStream 中间缓冲。
   *
   * @param outputStream 目标输出流（通常由 response.getOutputStream() 获得）
   * @param dataList     导出数据列表（已转换为 ExportVO）
   * @param voClass      ExportVO 类型（提供 {@code @ExcelProperty} 注解映射）
   * @param sheetName    Sheet 名称
   * @param <T>          ExportVO 类型
   * @throws Exception 写入失败
   */
  public static <T> void write(OutputStream outputStream, List<T> dataList,
      Class<T> voClass, String sheetName) throws Exception {
    try (ExcelWriter writer = ExcelFacade.write(outputStream, voClass).sheet(sheetName)) {
      writer.doWrite(dataList);
    }
  }
}
