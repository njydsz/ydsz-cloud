package com.njydsz.agent.web.util;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.ExcelWriter;
import com.njydsz.common.util.date.DateUtils;

/**
 * Agent 模块 Excel 导出工具类 — 封装 RFC 5987 文件名编码 + 流式写入 response。
 *
 * <p>P2-2: 消除 4 个 Agent Controller 导出方法中重复的 setHeader + ByteArrayOutputStream 模式。
 * 所有 Controller 层 Excel 导出端点统一通过本类 {@link #write} 流式写入，
 * 符合 YDIZ-COMMON-033（Excel Web 导出必须通过 ExcelWebSupport 流式写入 response OutputStream）。
 *
 * <h3>设计意图</h3>
 *
 * <ul>
 *   <li>文件名使用 yyyyMMddHHmmss 时间戳保持用户可识别性</li>
 *   <li>RFC 5987 双编码保证中文文件名在 IE/Edge/Chrome/Firefox 全兼容</li>
 *   <li>流式写入 response.getOutputStream() 避免 ByteArrayOutputStream 中间缓冲</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.30
 */
public final class ExcelExportUtil {

  /** OOXML Content-Type 常量 */
  private static final String CONTENT_TYPE_OOXML =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private ExcelExportUtil() {
    // 静态工具类，禁止实例化
  }

  /**
   * 流式写入 Excel 到 HTTP response，文件名带 yyyyMMddHHmmss 时间戳。
   *
   * <p>统一设置 Content-Type / Content-Disposition（RFC 5987 双编码），
   * 数据通过 {@link ExcelWriter} 流式写入 response.getOutputStream()，
   * 不经过 ByteArrayOutputStream 中间缓冲。
   *
   * @param response   HTTP 响应
   * @param dataList   导出数据列表（已转换为 ExportVO）
   * @param voClass    ExportVO 类型（提供 {@code @ExcelProperty} 注解映射）
   * @param baseName   文件名前缀（不含时间戳和扩展名），如 {@code "agent_definitions"}
   * @param sheetName  Sheet 名称
   * @param <T>        ExportVO 类型
   * @throws IOException 写入失败
   */
  public static <T> void write(HttpServletResponse response, List<T> dataList,
      Class<T> voClass, String baseName, String sheetName) throws IOException {
    String fileName = baseName + "_" + DateUtils.formatNow("yyyyMMddHHmmss") + ".xlsx";
    response.setContentType(CONTENT_TYPE_OOXML);
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(fileName));
    try (ExcelWriter writer = ExcelFacade.write(response.getOutputStream(), voClass)
        .sheet(sheetName)) {
      writer.doWrite(dataList);
    }
  }

  /**
   * 构建 RFC 6266 / RFC 5987 双编码 Content-Disposition 值。
   *
   * <p>生成格式：{@code attachment; filename="asciiFallback.xlsx"; filename*=UTF-8''%E4%B8%AD%E6%96%87.xlsx}
   *
   * @param fileName 原始文件名
   * @return 符合 RFC 6266 的 Content-Disposition 值
   */
  private static String buildContentDisposition(String fileName) {
    // ASCII 兜底：非 ASCII 字符替换为下划线
    String asciiFallback = fileName.replaceAll("[^\\x20-\\x7E]", "_");
    // RFC 5987 扩展（UTF-8 percent-encoding）
    String utf8Encoded =
        URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    return String.format(
        "attachment; filename=\"%s\"; filename*=UTF-8''%s", asciiFallback, utf8Encoded);
  }
}
