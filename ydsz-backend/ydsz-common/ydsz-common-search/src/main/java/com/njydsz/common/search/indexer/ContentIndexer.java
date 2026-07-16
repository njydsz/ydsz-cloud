package com.njydsz.common.search.indexer;

import java.io.InputStream;

import com.njydsz.common.search.core.IndexDocument;

import lombok.extern.slf4j.Slf4j;

/**
 * 内容索引器
 * <p>
 * 将文件内容解析后写入搜索索引。通过 {@link ContentExtractor} SPI 接口解析文档内容，
 * 当无可用实现时仅索引文件名等元数据。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>通过 {@code ContentExtractor} 接口注入，避免反射调用和硬依赖</li>
 *   <li>内容解析失败不影响元数据索引</li>
 *   <li>支持内容长度限制，防止超大文件撑爆索引</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
public class ContentIndexer {

    /** 最大内容索引长度（100KB，超出截断） */
    private static final int MAX_CONTENT_LENGTH = 100_000;

    private final ContentExtractor contentExtractor;

    public ContentIndexer(ContentExtractor contentExtractor) {
        this.contentExtractor = contentExtractor;
    }

    /**
     * 解析文件内容并更新索引文档
     *
     * @param document    索引文档（已填充元数据）
     * @param inputStream 文件输入流
     * @param fileName    文件名
     * @return 更新后的索引文档（content 字段已填充）
     */
    public IndexDocument enrichWithContent(IndexDocument document,
                                           InputStream inputStream,
                                           String fileName) {
        if (contentExtractor == null || inputStream == null) {
            return document;
        }

        try {
            String content = contentExtractor.extract(inputStream, fileName);
            if (content != null && !content.isBlank()) {
                // 截断超长内容
                if (content.length() > MAX_CONTENT_LENGTH) {
                    content = content.substring(0, MAX_CONTENT_LENGTH);
                }
                document.setContent(content);

                // 生成摘要（取前 200 字符）
                if (document.getSnippet() == null || document.getSnippet().isBlank()) {
                    String snippet = content.length() > 200
                            ? content.substring(0, 200) + "..."
                            : content;
                    document.setSnippet(snippet);
                }
            }
        } catch (Exception e) {
            log.warn("[ContentIndexer] 内容解析失败，仅索引元数据: file={}, error={}",
                    fileName, e.getMessage());
        }

        return document;
    }

    /**
     * 检查内容索引器是否可用
     */
    public boolean isAvailable() {
        return contentExtractor != null && contentExtractor.isAvailable();
    }
}
