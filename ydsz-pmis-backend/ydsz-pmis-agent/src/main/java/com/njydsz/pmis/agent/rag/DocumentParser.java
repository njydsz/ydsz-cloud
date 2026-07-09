package com.njydsz.pmis.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用文档解析器（P1-5 落地）。
 *
 * <p>对标 Coze 文档解析 / Dify 文档预处理 / FastGPT 文本分段：
 * <ul>
 *   <li>根据文档类型自动选择合适的解析策略</li>
 *   <li>支持纯文本、Markdown、HTML 格式</li>
 *   <li>Markdown 使用结构化分块（按标题切分）</li>
 *   <li>HTML 先提取纯文本再分块</li>
 *   <li>纯文本使用通用字符分块</li>
 * </ul>
 *
 * <p>后续扩展方向：
 * <ul>
 *   <li>PDF：集成 Apache PDFBox / Tika 提取文本</li>
 *   <li>Word：集成 Apache POI 提取文本</li>
 *   <li>Excel：按 Sheet → Row 结构化提取</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-5)
 */
@Slf4j
@Component
public class DocumentParser {

    /** 默认最大分块大小 */
    public static final int DEFAULT_MAX_CHUNK_SIZE = 500;

    /** 默认分块重叠 */
    public static final int DEFAULT_CHUNK_OVERLAP = 50;

    private final DocumentSplitter textSplitter;
    private final MarkdownDocumentSplitter markdownSplitter;

    public DocumentParser() {
        this(DEFAULT_MAX_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    public DocumentParser(int maxChunkSize, int chunkOverlap) {
        this.textSplitter = new DocumentSplitter(maxChunkSize, chunkOverlap);
        this.markdownSplitter = new MarkdownDocumentSplitter(maxChunkSize, chunkOverlap);
    }

    /**
     * 解析文档并切分为分块。
     *
     * @param content    文档内容
     * @param format     文档格式（txt / md / markdown / html / htm）
     * @return 分块列表
     */
    public List<String> parse(String content, String format) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String normalizedFormat = format == null ? "txt" : format.strip().toLowerCase();

        return switch (normalizedFormat) {
            case "md", "markdown" -> {
                log.debug("[DocParser] Markdown 格式，使用结构化分块");
                yield markdownSplitter.split(content);
            }
            case "html", "htm" -> {
                log.debug("[DocParser] HTML 格式，提取纯文本后分块");
                String plainText = stripHtml(content);
                yield textSplitter.split(plainText);
            }
            case "txt", "text", "" -> {
                log.debug("[DocParser] 纯文本格式");
                yield textSplitter.split(content);
            }
            default -> {
                log.warn("[DocParser] 未知格式 '{}'，按纯文本处理", format);
                yield textSplitter.split(content);
            }
        };
    }

    /**
     * 解析文档（自动检测格式）。
     *
     * <p>检测规则：
     * <ul>
     *   <li>包含 Markdown 标题标记（# 开头）→ Markdown</li>
     *   <li>包含 HTML 标签（&lt;html&gt;, &lt;p&gt;, &lt;div&gt; 等）→ HTML</li>
     *   <li>其他 → 纯文本</li>
     * </ul>
     *
     * @param content 文档内容
     * @return 分块列表
     */
    public List<String> parseAuto(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String detected = detectFormat(content);
        return parse(content, detected);
    }

    /**
     * 自动检测文档格式。
     *
     * @param content 文档内容
     * @return 格式标识：md / html / txt
     */
    private String detectFormat(String content) {
        // 检测 Markdown 标题
        String[] lines = content.split("\n", 20);
        int mdHeaders = 0;
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.startsWith("# ") || trimmed.startsWith("## ")
                    || trimmed.startsWith("### ")) {
                mdHeaders++;
            }
        }
        if (mdHeaders >= 2) {
            return "md";
        }

        // 检测 HTML 标签
        String lower = content.toLowerCase();
        if (lower.contains("<html") || lower.contains("<!doctype html")
                || (lower.contains("<p>") && lower.contains("</p>"))
                || (lower.contains("<div") && lower.contains("</div>"))) {
            return "html";
        }

        return "txt";
    }

    /**
     * 简易 HTML 标签剥离。
     *
     * <p>移除所有 HTML 标签，保留纯文本内容。
     * 将块级元素标签（&lt;p&gt;, &lt;div&gt;, &lt;br&gt;, &lt;li&gt; 等）替换为换行。
     *
     * @param html HTML 文本
     * @return 纯文本
     */
    private String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        // 移除 script / style 内容
        String result = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        result = result.replaceAll("(?is)<style[^>]*>.*?</style>", "");

        // 块级元素标签替换为换行
        result = result.replaceAll("(?i)<br\\s*/?>", "\n");
        result = result.replaceAll("(?i)</(p|div|li|h[1-6]|tr|table)>", "\n");

        // 移除所有 HTML 标签
        result = result.replaceAll("(?s)<[^>]+>", "");

        // 解码常见 HTML 实体
        result = result.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");

        // 清理多余空行
        result = result.replaceAll("\n{3,}", "\n\n");

        return result.strip();
    }
}
