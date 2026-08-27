package com.njydsz.agent.server.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

import com.njydsz.agent.domain.document.DocumentFormat;
import com.njydsz.agent.domain.document.DocumentRenderService;
import com.njydsz.agent.domain.document.DocumentTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * 文档渲染服务实现。
 *
 * <p>提供 Markdown 到多种文档格式的转换功能。
 * 支持 Markdown、HTML、纯文本、Word (DOCX)、PDF 等格式。</p>
 *
 * <p>注意：DOCX 和 PDF 格式需要引入额外依赖（如 docx4j、iText 等），
 * 当前实现提供基础框架，实际渲染逻辑可根据需要扩展。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
public class DocumentRenderServiceImpl implements DocumentRenderService {

    @Override
    public void render(String markdown, DocumentFormat format, OutputStream output) {
        Objects.requireNonNull(markdown, "markdown 不能为 null");
        Objects.requireNonNull(format, "format 不能为 null");
        Objects.requireNonNull(output, "output 不能为 null");

        log.debug("[DocumentRender] 渲染文档: format={}, size={}", format, markdown.length());

        switch (format) {
            case MARKDOWN:
                writeMarkdown(markdown, output);
                break;
            case HTML:
                renderToHtml(markdown, output, null);
                break;
            case TXT:
                renderToPlainText(markdown, output);
                break;
            case DOCX:
                renderToDocx(markdown, output, null);
                break;
            case PDF:
                renderToPdf(markdown, output, null);
                break;
            default:
                throw new DocumentRenderException("不支持的文档格式: " + format);
        }
    }

    @Override
    public byte[] renderToBytes(String markdown, DocumentFormat format) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        render(markdown, format, baos);
        return baos.toByteArray();
    }

    @Override
    public void renderWithTemplate(DocumentTemplate template, DocumentFormat format, OutputStream output) {
        Objects.requireNonNull(template, "template 不能为 null");

        log.debug("[DocumentRender] 使用模板渲染: template={}, format={}",
                template.getTemplateName(), format);

        // 根据模板变量生成 Markdown
        String markdown = generateMarkdownFromTemplate(template);

        switch (format) {
            case HTML:
                renderToHtml(markdown, output, template);
                break;
            case DOCX:
                renderToDocx(markdown, output, template);
                break;
            case PDF:
                renderToPdf(markdown, output, template);
                break;
            default:
                render(markdown, format, output);
        }
    }

    @Override
    public void convert(String inputMarkdown, DocumentFormat sourceFormat,
                        DocumentFormat targetFormat, OutputStream output) {
        // 简化实现：直接渲染为目标格式
        // 实际场景可能需要先解析源格式再转换
        render(inputMarkdown, targetFormat, output);
    }

    /**
     * 直接写入 Markdown。
     */
    private void writeMarkdown(String markdown, OutputStream output) {
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(markdown);
            writer.flush();
        } catch (IOException e) {
            throw new DocumentRenderException("写入 Markdown 失败", e);
        }
    }

    /**
     * 渲染为 HTML。
     */
    private void renderToHtml(String markdown, OutputStream output, DocumentTemplate template) {
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(output, StandardCharsets.UTF_8))) {

            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"zh-CN\">");
            writer.println("<head>");
            writer.println("  <meta charset=\"UTF-8\">");
            writer.printf("  <title>%s</title>%n",
                    template != null && template.getTitle() != null ? template.getTitle() : "文档");
            writer.println("  <style>");
            writer.println("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; max-width: 800px; margin: 0 auto; padding: 20px; }");
            writer.println("    h1, h2, h3 { color: #333; }");
            writer.println("    code { background: #f4f4f4; padding: 2px 4px; border-radius: 3px; }");
            writer.println("    pre { background: #f4f4f4; padding: 10px; border-radius: 5px; overflow-x: auto; }");
            writer.println("    blockquote { border-left: 4px solid #ddd; margin: 0; padding-left: 16px; color: #666; }");
            writer.println("    table { border-collapse: collapse; width: 100%; }");
            writer.println("    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
            writer.println("  </style>");
            writer.println("</head>");
            writer.println("<body>");

            if (template != null && template.getHeaderText() != null) {
                writer.printf("  <header>%s</header>%n", template.getHeaderText());
            }

            // 简化的 Markdown 到 HTML 转换
            String htmlContent = simpleMarkdownToHtml(markdown);
            writer.println(htmlContent);

            if (template != null && template.getFooterText() != null) {
                writer.printf("  <footer>%s</footer>%n", template.getFooterText());
            }

            writer.println("</body>");
            writer.println("</html>");
            writer.flush();

        } catch (Exception e) {
            throw new DocumentRenderException("渲染 HTML 失败", e);
        }
    }

    /**
     * 渲染为纯文本。
     */
    private void renderToPlainText(String markdown, OutputStream output) {
        try (OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            // 移除 Markdown 标记
            String plainText = markdown
                    .replaceAll("#{1,6}\\s+", "") // 标题
                    .replaceAll("\\*\\*([^*]+)\\*\\*", "$1") // 粗体
                    .replaceAll("\\*([^*]+)\\*", "$1") // 斜体
                    .replaceAll("`([^`]+)`", "$1") // 行内代码
                    .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1") // 链接
                    .replaceAll("^[-*+]\\s+", "• ") // 列表
                    .replaceAll("^\\d+\\.\\s+", "• "); // 有序列表

            writer.write(plainText);
            writer.flush();
        } catch (IOException e) {
            throw new DocumentRenderException("渲染纯文本失败", e);
        }
    }

    /**
     * 渲染为 DOCX（简化实现）。
     *
     * <p>完整实现需要引入 docx4j 等库。当前输出包含基本结构的 XML。</p>
     */
    private void renderToDocx(String markdown, OutputStream output, DocumentTemplate template) {
        log.warn("[DocumentRender] DOCX 渲染需要引入 docx4j 依赖，当前为简化实现");

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            // 输出简化的 DOCX 结构提示
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<!-- DOCX 格式需要引入 docx4j 库进行完整渲染 -->");
            writer.println("<!-- 以下为文档内容预览 -->");
            writer.println();
            writer.println("标题: " + (template != null ? template.getTitle() : "未命名"));
            writer.println("作者: " + (template != null ? template.getAuthor() : "YDSZ Agent"));
            writer.println("日期: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
            writer.println();
            writer.println("--- 内容 ---");
            writer.println(markdown);
            writer.flush();
        } catch (Exception e) {
            throw new DocumentRenderException("渲染 DOCX 失败", e);
        }
    }

    /**
     * 渲染为 PDF（简化实现）。
     *
     * <p>完整实现需要引入 iText 或 Apache PDFBox 等库。</p>
     */
    private void renderToPdf(String markdown, OutputStream output, DocumentTemplate template) {
        log.warn("[DocumentRender] PDF 渲染需要引入 iText/PDFBox 依赖，当前为简化实现");

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.println("%PDF-.1");
            writer.println("% 注意: PDF 格式需要引入 iText 或 PDFBox 库进行完整渲染");
            writer.println("% 以下为文档内容预览");
            writer.println();
            writer.println("标题: " + (template != null ? template.getTitle() : "未命名"));
            writer.println("作者: " + (template != null ? template.getAuthor() : "YDSZ Agent"));
            writer.println("日期: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
            writer.println();
            writer.println("--- 内容 ---");
            writer.println(markdown);
            writer.flush();
        } catch (Exception e) {
            throw new DocumentRenderException("渲染 PDF 失败", e);
        }
    }

    /**
     * 简化的 Markdown 到 HTML 转换。
     *
     * @param markdown Markdown 内容
     * @return HTML 内容
     */
    private String simpleMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        String html = markdown;

        // 标题
        html = html.replaceAll("^######\\s+(.+)$", "<h6>$1</h6>");
        html = html.replaceAll("^#####\\s+(.+)$", "<h5>$1</h5>");
        html = html.replaceAll("^####\\s+(.+)$", "<h4>$1</h4>");
        html = html.replaceAll("^###\\s+(.+)$", "<h3>$1</h3>");
        html = html.replaceAll("^##\\s+(.+)$", "<h2>$1</h2>");
        html = html.replaceAll("^#\\s+(.+)$", "<h1>$1</h1>");

        // 粗体和斜体
        html = html.replaceAll("\\*\\*\\*([^*]+)\\*\\*\\*", "<strong><em>$1</em></strong>");
        html = html.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("\\*([^*]+)\\*", "<em>$1</em>");

        // 行内代码
        html = html.replaceAll("`([^`]+)`", "<code>$1</code>");

        // 链接
        html = html.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");

        // 无序列表
        html = html.replaceAll("^\\s*[-*+]\\s+(.+)$", "<li>$1</li>");

        // 引用
        html = html.replaceAll("^>\\s+(.+)$", "<blockquote>$1</blockquote>");

        // 段落
        html = html.replaceAll("\n\n", "</p><p>");
        html = "<p>" + html + "</p>";

        return html;
    }

    /**
     * 根据模板生成 Markdown。
     */
    private String generateMarkdownFromTemplate(DocumentTemplate template) {
        StringBuilder sb = new StringBuilder();

        if (template.getTitle() != null) {
            sb.append("# ").append(template.getTitle()).append("\n\n");
        }

        if (template.getSystem() != null) {
            sb.append("> 作者: ").append(template.getAuthor()).append("\n");
            sb.append("> 日期: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
            sb.append("\n\n");
        }

        // 添加模板变量
        Map<String, Object> variables = template.getVariables();
        if (variables != null && !variables.isEmpty()) {
            sb.append("## 参数\n\n");
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                sb.append("- **").append(entry.getKey()).append("**: ").append(entry.getValue()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 文档渲染异常。
     */
    public static class DocumentRenderException extends RuntimeException {
        public DocumentRenderException(String message) {
            super(message);
        }

        public DocumentRenderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
