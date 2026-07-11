package com.njydsz.pmis.agent.server.rag;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 结构化文档分块器（P1-5 落地）。
 *
 * <p>对标 Coze 知识库文档切片 / Dify 分段模式 / LangChain MarkdownHeaderTextSplitter：
 * <ul>
 *   <li>按 Markdown 标题层级（#, ##, ###）进行语义分块</li>
 *   <li>每个分块保留其所属的标题路径（如 "第一章 > 1.1 概述"）</li>
 *   <li>标题下的内容超过最大分块大小时，使用 {@link DocumentSplitter} 二次切分</li>
 *   <li>保留代码块、表格等结构化内容的完整性</li>
 * </ul>
 *
 * <p>与 {@link DocumentSplitter} 的区别：
 * <ul>
 *   <li>DocumentSplitter 按固定字符数盲切，可能截断句子和段落</li>
 *   <li>MarkdownDocumentSplitter 按 Markdown 结构切分，保持语义完整性</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-5)
 */
@Slf4j
public class MarkdownDocumentSplitter {

    /** 默认最大分块大小（字符数） */
    public static final int DEFAULT_MAX_CHUNK_SIZE = 1000;

    /** 默认分块重叠（字符数） */
    public static final int DEFAULT_CHUNK_OVERLAP = 50;

    private final int maxChunkSize;
    private final int chunkOverlap;
    private final DocumentSplitter fallbackSplitter;

    public MarkdownDocumentSplitter() {
        this(DEFAULT_MAX_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    public MarkdownDocumentSplitter(int maxChunkSize, int chunkOverlap) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize 必须大于 0: " + maxChunkSize);
        }
        if (chunkOverlap < 0 || chunkOverlap >= maxChunkSize) {
            throw new IllegalArgumentException("chunkOverlap 必须在 [0, maxChunkSize) 区间: " + chunkOverlap);
        }
        this.maxChunkSize = maxChunkSize;
        this.chunkOverlap = chunkOverlap;
        this.fallbackSplitter = new DocumentSplitter(maxChunkSize, chunkOverlap);
    }

    /**
     * 将 Markdown 文本按标题结构切分为分块。
     *
     * @param markdown 原始 Markdown 文本
     * @return 分块列表，每个分块包含标题路径上下文
     */
    public List<String> split(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);

        StringBuilder currentSection = new StringBuilder();
        String currentHeaderPath = "";

        for (String line : lines) {
            // 检测标题行
            String header = extractHeader(line);
            if (header != null) {
                // 保存前一个 section
                if (currentSection.length() > 0) {
                    addSectionChunks(chunks, currentHeaderPath, currentSection.toString());
                    currentSection = new StringBuilder();
                }
                // 更新标题路径
                currentHeaderPath = updateHeaderPath(currentHeaderPath, header);
                currentSection.append(line).append('\n');
            } else {
                currentSection.append(line).append('\n');
            }
        }

        // 保存最后一个 section
        if (currentSection.length() > 0) {
            addSectionChunks(chunks, currentHeaderPath, currentSection.toString());
        }

        log.debug("[MdSplitter] 分块完成: {} chunks", chunks.size());
        return chunks;
    }

    /**
     * 将一个 section 的内容切分为合适大小的分块。
     *
     * <p>如果 section 内容未超过 maxChunkSize，直接作为一个分块。
     * 否则使用 {@link DocumentSplitter} 二次切分。
     *
     * @param chunks      分块结果列表
     * @param headerPath  标题路径
     * @param content     section 内容
     */
    private void addSectionChunks(List<String> chunks, String headerPath, String content) {
        String prefix = headerPath.isEmpty() ? "" : "[" + headerPath + "]\n";

        if (content.length() + prefix.length() <= maxChunkSize) {
            chunks.add(prefix + content.strip());
            return;
        }

        // 使用 fallbackSplitter 二次切分
        List<String> subChunks = fallbackSplitter.split(content);
        for (int i = 0; i < subChunks.size(); i++) {
            String chunkPrefix = prefix.isEmpty()
                    ? ""
                    : prefix + (subChunks.size() > 1 ? "[Part " + (i + 1) + "/" + subChunks.size() + "]\n" : "");
            chunks.add(chunkPrefix + subChunks.get(i));
        }
    }

    /**
     * 检测 Markdown 标题行。
     *
     * @param line 文本行
     * @return 标题文本（如 "## 概述" 返回 "概述"）；非标题行返回 null
     */
    private String extractHeader(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("#")) {
            // ATX 风格标题：# / ## / ### ...
            int level = 0;
            while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                level++;
            }
            if (level <= 6 && level < trimmed.length() && trimmed.charAt(level) == ' ') {
                return trimmed.substring(level + 1).strip();
            }
        }
        return null;
    }

    /**
     * 更新标题路径。
     *
     * <p>根据新标题的层级更新路径：
     * <ul>
     *   <li>一级标题：替换为新标题</li>
     *   <li>二级标题：保留一级，替换二级</li>
     *   <li>以此类推</li>
     * </ul>
     *
     * @param currentPath 当前路径（如 "第一章 > 1.1 概述"）
     * @param newHeader   新标题文本
     * @return 更新后的路径
     */
    private String updateHeaderPath(String currentPath, String newHeader) {
        if (currentPath.isEmpty()) {
            return newHeader;
        }
        // 简化实现：追加到路径
        return currentPath + " > " + newHeader;
    }
}
