package com.njydsz.nextwiki.server.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文档转换服务。
 *
 * <p>Office → PDF、PDF → 图片等格式互转。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class DocumentConversionApplicationService {

  private final PreviewApplicationService previewService;

  public DocumentConversionApplicationService(PreviewApplicationService previewService) {
    this.previewService = previewService;
  }

  /**
   * 转换文档格式（按 {@code 源格式->目标格式} 路由到具体转换实现）。
   *
   * <p>当前支持 md/markdown→html、html→pdf（占位，待集成 OpenPDF/Flying Saucer）、 txt→html、csv→html；不支持的组合抛出
   * {@link UnsupportedOperationException}。 Markdown/文本/CSV 转 HTML 为内置轻量实现，HTML→PDF 暂仅回写 HTML 作为占位。
   *
   * @param inputFormat 源格式（如 "md"、"html"、"txt"、"csv"），大小写不敏感
   * @param outputFormat 目标格式（如 "html"、"pdf"）
   * @param inputStream 源文件输入流（方法内会读尽，调用方不必复用）
   * @param outputStream 目标文件输出流（方法内写入转换结果，调用方负责关闭）
   * @throws IOException 不支持的格式组合抛 {@link UnsupportedOperationException}；IO/编码异常原样抛出
   * @complexity O(contentLength)（全量读入内存后做正则/字符串替换）
   * @note 本服务为无状态纯转换，线程安全；大文件会整体载入内存，注意内存占用
   * @see #convertMarkdownToHtml(InputStream, OutputStream)
   * @see #convertHtmlToPdf(InputStream, OutputStream)
   */
  public void convert(
      String inputFormat, String outputFormat, InputStream inputStream, OutputStream outputStream)
      throws IOException {
    String key = (inputFormat + "->" + outputFormat).toLowerCase();
    log.info("[DocumentConversionApplicationService] 格式转换: {}", key);

    switch (key) {
      case "md->html", "markdown->html" -> convertMarkdownToHtml(inputStream, outputStream);
      case "html->pdf" -> convertHtmlToPdf(inputStream, outputStream);
      case "txt->html" -> convertTextToHtml(inputStream, outputStream);
      case "csv->html" -> convertCsvToHtml(inputStream, outputStream);
      default -> throw new UnsupportedOperationException("不支持的格式转换: " + key);
    }
  }

  /** Markdown 转 HTML */
  private void convertMarkdownToHtml(InputStream inputStream, OutputStream outputStream)
      throws IOException {
    String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    // 简化的 Markdown 转 HTML（实际应使用 flexmark-java 或 commonmark-java）
    String html =
        markdown
            .replaceAll("(?m)^# (.+)$", "<h1>$1</h1>")
            .replaceAll("(?m)^## (.+)$", "<h2>$1</h2>")
            .replaceAll("(?m)^### (.+)$", "<h3>$1</h3>")
            .replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")
            .replaceAll("\\*(.+?)\\*", "<em>$1</em>")
            .replaceAll("`(.+?)`", "<code>$1</code>")
            .replaceAll("(?m)^- (.+)$", "<li>$1</li>")
            .replaceAll("(?m)^> (.+)$", "<blockquote>$1</blockquote>")
            .replaceAll("\\n\\n", "</p><p>")
            .replaceAll("\\n", "<br>");

    String fullHtml =
        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
            + "body{font-family:sans-serif;max-width:800px;margin:20px auto;padding:20px;}"
            + "code{background:#f4f4f4;padding:2px 4px;border-radius:3px;}"
            + "blockquote{border-left:4px solid #ddd;margin:0;padding-left:16px;color:#666;}"
            + "</style></head><body><p>"
            + html
            + "</p></body></html>";

    outputStream.write(fullHtml.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * HTML 转 PDF
   *
   * <p>TODO: 集成 OpenPDF 或 Flying Saucer 库实现真正的 HTML→PDF 转换。 当前版本仅输出 HTML 内容作为占位。
   */
  private void convertHtmlToPdf(InputStream inputStream, OutputStream outputStream)
      throws IOException {
    String html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    log.info(
        "[DocumentConversionApplicationService] HTML→PDF 转换（TODO: 待集成 OpenPDF/Flying Saucer, HTML 长度: {}）",
        html.length());
    // TODO: 2026-09-01 使用 OpenPDF 或 Flying Saucer 库将 HTML 转为 PDF。（@ydsz-team）
    outputStream.write(html.getBytes(StandardCharsets.UTF_8));
  }

  /** 纯文本转 HTML */
  private void convertTextToHtml(InputStream inputStream, OutputStream outputStream)
      throws IOException {
    String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    String escaped =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    String html =
        "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
            + "<body><pre style=\"white-space:pre-wrap;word-wrap:break-word;\">"
            + escaped
            + "</pre></body></html>";
    outputStream.write(html.getBytes(StandardCharsets.UTF_8));
  }

  /** CSV 转 HTML 表格 */
  private void convertCsvToHtml(InputStream inputStream, OutputStream outputStream)
      throws IOException {
    String csv = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    String[] lines = csv.split("\n");
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
    html.append("<style>table{border-collapse:collapse;width:100%;}");
    html.append("th,td{border:1px solid #ddd;padding:8px;text-align:left;}");
    html.append("th{background:#f4f4f4;}</style></head><body><table>");

    for (int i = 0; i < lines.length; i++) {
      String[] cells = lines[i].split(",");
      html.append(i == 0 ? "<tr>" : "<tr>");
      for (String cell : cells) {
        html.append(i == 0 ? "<th>" : "<td>")
            .append(cell.trim())
            .append(i == 0 ? "</th>" : "</td>");
      }
      html.append("</tr>");
    }

    html.append("</table></body></html>");
    outputStream.write(html.toString().getBytes(StandardCharsets.UTF_8));
  }
}
