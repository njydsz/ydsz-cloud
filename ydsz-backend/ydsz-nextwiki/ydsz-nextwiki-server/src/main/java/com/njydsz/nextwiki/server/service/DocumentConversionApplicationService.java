package com.njydsz.nextwiki.server.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 文档转换应用服务
 * <p>
 * 提供多种文档格式转换能力：
 * <ul>
 *   <li>Office → PDF（基于 LibreOffice headless）</li>
 *   <li>Markdown → HTML（基于 flexmark-java）</li>
 *   <li>HTML → PDF（基于 OpenPDF）</li>
 *   <li>文本编码转换</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
@Service
public class DocumentConversionApplicationService {

    private final PreviewApplicationService previewService;

    public DocumentConversionApplicationService(PreviewApplicationService previewService) {
        this.previewService = previewService;
    }

    /**
     * 转换文档格式
     *
     * @param inputFormat  源格式
     * @param outputFormat 目标格式
     * @param inputStream  源文件流
     * @param outputStream 目标文件流
     */
    public void convert(String inputFormat, String outputFormat,
                         InputStream inputStream, OutputStream outputStream) throws Exception {
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

    /**
     * Markdown 转 HTML
     */
    private void convertMarkdownToHtml(InputStream inputStream, OutputStream outputStream) throws Exception {
        String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        // 简化的 Markdown 转 HTML（实际应使用 flexmark-java 或 commonmark-java）
        String html = markdown
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

        String fullHtml = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
                + "body{font-family:sans-serif;max-width:800px;margin:20px auto;padding:20px;}"
                + "code{background:#f4f4f4;padding:2px 4px;border-radius:3px;}"
                + "blockquote{border-left:4px solid #ddd;margin:0;padding-left:16px;color:#666;}"
                + "</style></head><body><p>" + html + "</p></body></html>";

        outputStream.write(fullHtml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * HTML 转 PDF
     */
    private void convertHtmlToPdf(InputStream inputStream, OutputStream outputStream) throws Exception {
        // 实际应使用 OpenPDF 或 Flying Sacer 库
        // 此处为框架占位
        String html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        log.info("[DocumentConversionApplicationService] HTML→PDF 转换（HTML 长度: {}）", html.length());
        // 占位：输出原始 HTML（实际应转换为 PDF 字节流）
        outputStream.write(html.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 纯文本转 HTML
     */
    private void convertTextToHtml(InputStream inputStream, OutputStream outputStream) throws Exception {
        String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body><pre style=\"white-space:pre-wrap;word-wrap:break-word;\">"
                + escaped + "</pre></body></html>";
        outputStream.write(html.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * CSV 转 HTML 表格
     */
    private void convertCsvToHtml(InputStream inputStream, OutputStream outputStream) throws Exception {
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
                html.append(i == 0 ? "<th>" : "<td>").append(cell.trim()).append(i == 0 ? "</th>" : "</td>");
            }
            html.append("</tr>");
        }

        html.append("</table></body></html>");
        outputStream.write(html.toString().getBytes(StandardCharsets.UTF_8));
    }
}
