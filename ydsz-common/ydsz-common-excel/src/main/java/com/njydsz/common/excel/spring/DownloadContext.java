package com.njydsz.common.excel.spring;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Excel 下载上下文
 *
 * <p>封装 HTTP 响应所需的下载元数据：文件名、Content-Type 等。 供 {@link ExcelWebSupport} 在写入 HttpServletResponse 时使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class DownloadContext {

  /** 默认文件名前缀 */
  private static final String DEFAULT_FILENAME_PREFIX = "export";

  /** 文件名日期格式 */
  private static final DateTimeFormatter FILENAME_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  /** 下载文件名（不含扩展名） */
  private final String filename;

  /** Excel MIME 类型 */
  private final String contentType;

  public DownloadContext(String filename, String contentType) {
    this.filename = filename;
    this.contentType = contentType;
  }

  public DownloadContext(String filename) {
    this(filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
  }

  public DownloadContext() {
    this(DEFAULT_FILENAME_PREFIX + "_" + LocalDateTime.now().format(FILENAME_DATE_FORMAT));
  }

  /**
   * 获取完整文件名（含 .xlsx 扩展名）。
   *
   * @return 文件名
   */
  public String getFullFilename() {
    return filename.endsWith(".xlsx") ? filename : filename + ".xlsx";
  }

  public String getFilename() {
    return filename;
  }

  public String getContentType() {
    return contentType;
  }
}
