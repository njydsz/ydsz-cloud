package com.njydsz.pmis.common.search.indexer;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;

import com.njydsz.pmis.common.search.core.IndexDocument;

import lombok.extern.slf4j.Slf4j;

/**
 * 内容索引器
 * <p>
 * 将文件内容解析后写入搜索索引。当 {@code common-docs} 模块可用时，
 * 使用其 DocumentService 解析 PDF/Word/Excel/HTML/Markdown 等文档格式。
 * 当 {@code common-docs} 不可用时，仅索引文件名等元数据。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>通过反射可选依赖 {@code common-docs} 的 {@code DocumentService}，避免硬依赖</li>
 *   <li>内容解析失败不影响元数据索引</li>
 *   <li>支持内容长度限制，防止超大文件撑爆索引</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class ContentIndexer {

    /** 最大内容索引长度（100KB，超出截断） */
    private static final int MAX_CONTENT_LENGTH = 100_000;

    /** 可选的文档服务（common-docs DocumentService） */
    private final Object documentService;

    public ContentIndexer(Object documentService) {
        this.documentService = documentService;
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
        if (documentService == null || inputStream == null) {
            return document;
        }

        try {
            String content = parseContent(inputStream, fileName);
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
     * 调用 common-docs DocumentService 解析内容
     */
    private String parseContent(InputStream inputStream, String fileName) {
        if (documentService == null) {
            return null;
        }

        try {
            // 通过反射调用 DocumentService.parseAndPreprocess
            // 避免对 common-docs 的编译期硬依赖
            var parseMethod = documentService.getClass()
                    .getMethod("parseAndPreprocess",
                            InputStream.class, String.class,
                            Class.forName("com.njydsz.pmis.common.docs.domain.ParseOptions"));

            // 创建默认解析选项
            var parseOptionsClass = Class.forName("com.njydsz.pmis.common.docs.domain.ParseOptions");
            var parseOptions = parseOptionsClass.getConstructor().newInstance();

            var result = parseMethod.invoke(documentService, inputStream, fileName, parseOptions);
            if (result == null) {
                return null;
            }

            // 从 DocumentParseResult 中提取文本
            var isSuccess = (boolean) result.getClass().getMethod("isSuccess").invoke(result);
            if (!isSuccess) {
                return null;
            }

            var content = result.getClass().getMethod("getContent").invoke(result);
            if (content == null) {
                return null;
            }

            // 调用 DocumentContent.getText() 获取纯文本
            var text = content.getClass().getMethod("getText").invoke(content);
            return text != null ? text.toString() : null;

        } catch (ClassNotFoundException e) {
            log.debug("[ContentIndexer] common-docs 不可用，跳过内容解析");
            return null;
        } catch (Exception e) {
            log.warn("[ContentIndexer] 内容解析异常: file={}, error={}", fileName, e.getMessage());
            return null;
        }
    }

    /**
     * 检查内容索引器是否可用
     */
    public boolean isAvailable() {
        return documentService != null;
    }
}
