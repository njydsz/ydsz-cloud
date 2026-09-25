package com.njydsz.common.excel.spring;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpHeaders;

import com.njydsz.common.excel.core.ExcelFacade;
import com.njydsz.common.excel.core.ExcelWriter;
import com.njydsz.common.excel.core.config.ExcelConfig;

/**
 * Excel Web 导出支持
 *
 * <p>提供直接将 Excel 写入 {@link HttpServletResponse} 的便捷方法， 适用于 Controller 层直接下载场景。仅当 servlet API 在
 * classpath 上时生效。
 *
 * <p><b>流式导出</b>：数据直接从内存序列化到 {@link HttpServletResponse#getOutputStream()}，
 * 无需 {@code ByteArrayOutputStream} 中间缓冲。适合大文件场景，第一个字节在表头写入后即可推送给客户端。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @GetMapping("/export")
 * public void export(HttpServletResponse response) {
 *     List<User> users = userService.list();
 *     excelWebSupport.write(response, User.class, users, "用户数据");
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConditionalOnClass(name = "jakarta.servlet.http.HttpServletResponse")
public class ExcelWebSupport {

  private final ExcelConfig config;

  public ExcelWebSupport(ExcelConfig config) {
    this.config = config;
  }

  public ExcelWebSupport() {
    this(ExcelConfig.defaults());
  }

  /**
   * 写入 Excel 到 HTTP 响应。
   *
   * <p><b>流式写入</b>：直接向 {@link HttpServletResponse#getOutputStream()} 写入，
   * 不经过中间 ByteArrayOutputStream 缓冲，适合大文件场景。
   *
   * @param response HTTP 响应
   * @param clazz 数据类
   * @param data 数据列表
   * @param sheetName Sheet 名称
   * @param <T> 数据类型
   * @throws IOException 写入失败时抛出
   */
  public <T> void write(
      HttpServletResponse response, Class<T> clazz, List<T> data, String sheetName)
      throws IOException {
    DownloadContext context = new DownloadContext(sheetName);
    write(response, clazz, data, context);
  }

  /**
   * 写入 Excel 到 HTTP 响应（使用自定义下载上下文）。
   *
   * <p><b>流式写入</b>：直接向 {@link HttpServletResponse#getOutputStream()} 写入，
   * 不经过中间 ByteArrayOutputStream 缓冲，适合大文件场景。
   *
   * @param response HTTP 响应
   * @param clazz 数据类
   * @param data 数据列表
   * @param context 下载上下文
   * @param <T> 数据类型
   * @throws IOException 写入失败时抛出
   */
  public <T> void write(
      HttpServletResponse response, Class<T> clazz, List<T> data, DownloadContext context)
      throws IOException {
    response.setContentType(context.getContentType());
    response.setHeader(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=" + encodeFilename(context.getFullFilename()));

    // 流式写入：直接输出到 response.getOutputStream()，无需 ByteArrayOutputStream 中间缓冲
    // 注意：Content-Length 无法预知（流式写入），由 Servlet 容器启用 chunked transfer
    ExcelWriter writer = ExcelFacade.write(response.getOutputStream(), clazz)
        .config(config)
        .sheet(context.getFilename());
    writer.doWrite(data);
    writer.finish();
    response.getOutputStream().flush();
  }

  /**
   * 将预渲染的 Excel 字节数组写入 HTTP 响应。
   *
   * <p>适用于服务层已生成 {@code byte[]} 的场景（如自定义导出逻辑）， 统一处理 Content-Type / Content-Disposition / 文件名编码，消除
   * Controller 层手动拼接 HttpHeaders 的重复编码。
   *
   * @param response HTTP 响应
   * @param bytes Excel 文件字节数组
   * @param filename 下载文件名（含扩展名）
   * @throws IOException 写入失败时抛出
   */
  public void writeBytes(HttpServletResponse response, byte[] bytes, String filename)
      throws IOException {
    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader(
        HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + encodeFilename(filename));
    response.setContentLength(bytes.length);
    response.getOutputStream().write(bytes);
    response.getOutputStream().flush();
  }

  /**
   * 构建符合 RFC 6266 / RFC 5987 的 Content-Disposition 文件名编码。
   *
   * <p>生成双重格式：
   * <pre>{@code
   * attachment; filename="fallback.txt"; filename*=UTF-8''%E4%B8%AD%E6%96%87.txt
   * }</pre>
   *
   * <ul>
   *   <li>{@code filename}（传统 ASCII 兜底）：含非 ASCII 字符时用下划线替换，保证旧浏览器兼容</li>
   *   <li>{@code filename*}（RFC 5987 扩展）：完整的 UTF-8 编码，现代浏览器优先使用</li>
   * </ul>
   *
   * @param filename 文件名
   * @return RFC 6266 双重编码的文件名表达式
   */
  private String encodeFilename(String filename) {
    if (filename == null || filename.isEmpty()) {
      return "attachment; filename=\"export.xlsx\"";
    }
    // ASCII 兜底：非 ASCII → 下划线
    String asciiFallback = filename.replaceAll("[^\\x20-\\x7E]", "_");
    // RFC 5987 扩展（UTF-8 percent-encoding）
    String utf8Encoded =
        URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
    return String.format(
        "attachment; filename=\"%s\"; filename*=UTF-8''%s", asciiFallback, utf8Encoded);
  }
}
