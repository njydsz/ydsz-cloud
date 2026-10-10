package com.njydsz.common.web.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件导出工具类 — 统一设置 HTTP 响应头。
 *
 * <p>提供 Excel / CSV 文件下载的响应头封装，{@code filename} 使用 UTF-8 编码，
 * 避免中文文件名在不同浏览器下的乱码问题。
 *
 * <p>使用示例：
 * <pre>{@code
 * // Excel 导出
 * ExportHelper.prepareExcelDownload(response, "用户列表-" + DateUtils.formatNow(".xlsx"));
 * byte[] excelBytes = excelService.exportUsers();
 * response.getOutputStream().write(excelBytes);
 *
 * // CSV 导出
 * ExportHelper.prepareCsvDownload(response, "数据导出.csv");
 * response.getWriter().write(csvContent);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.10
 */
public final class ExportHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExportHelper.class);

  /** Excel 2007+ MIME 类型 */
  public static final String CONTENT_TYPE_EXCEL =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  /** CSV MIME 类型（UTF-8 with BOM 确保 Excel 正确识别编码） */
  public static final String CONTENT_TYPE_CSV = "text/csv; charset=UTF-8";

  private ExportHelper() {
  }

  /**
   * 准备 Excel 文件下载的 HTTP 响应头。
   *
   * <p>设置 Content-Type 为 xlsx MIME，Content-Disposition 为 attachment。
   * 中文文件名自动使用 UTF-8 URL 编码（RFC 5987），确保 IE / Chrome / Firefox / Safari 均正常显示。
   *
   * @param response HTTP 响应对象
   * @param filename 原始文件名（可含中文，如 {@code "用户列表-2026.xlsx"}）
   */
  public static void prepareExcelDownload(HttpServletResponse response, String filename) {
    prepareDownload(response, CONTENT_TYPE_EXCEL, filename);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("ExportHelper 准备 Excel 导出响应头，filename={}", filename);
    }
  }

  /**
   * 准备 CSV 文件下载的 HTTP 响应头。
   *
   * @param response HTTP 响应对象
   * @param filename 原始文件名（可含中文）
   */
  public static void prepareCsvDownload(HttpServletResponse response, String filename) {
    prepareDownload(response, CONTENT_TYPE_CSV, filename);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("ExportHelper 准备 CSV 导出响应头，filename={}", filename);
    }
  }

  /**
   * 准备通用文件下载的 HTTP 响应头。
   *
   * @param response  HTTP 响应对象
   * @param mimeType  MIME 类型（如 {@code "application/pdf"}）
   * @param filename  原始文件名（可含中文）
   */
  public static void prepareDownload(HttpServletResponse response, String mimeType, String filename) {
    response.setContentType(mimeType);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
    response.setHeader("Content-Disposition",
        "attachment; filename=" + encodedFilename
            + "; filename*=UTF-8''" + encodedFilename);
    // 禁用缓存以确保每次都重新下载
    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    response.setHeader("Pragma", "no-cache");
    response.setDateHeader("Expires", 0);
  }
}
