package com.njydsz.nextwiki.server.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.service.SearchDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档内容提取应用服务（P1-2）
 * <p>
 * 从上传的文件中提取文本内容，写入搜索索引。
 * 支持纯文本、Markdown、HTML、JSON、CSV 等格式的文本提取。
 * PDF/Office 文档需要集成 Apache Tika 或 PDFBox（可选依赖）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentExtractionApplicationService {

    private final FileNodeRepository fileNodeRepository;
    private final SearchDomainService searchDomainService;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /** 直接可读取文本内容的文件后缀 */
    private static final Set<String> TEXT_SUFFIXES = Set.of(
            "txt", "md", "csv", "json", "xml", "html", "htm", "log", "properties", "yml", "yaml", "sql"
    );

    /** 最大提取文本长度（1MB） */
    private static final int MAX_CONTENT_LENGTH = 1024 * 1024;

    /**
     * 异步提取文件内容并更新搜索索引
     */
    @Async("nextwikiTaskExecutor")
    public void extractAndIndexAsync(String fileNodeId, String userId) {
        try {
            extractAndIndex(fileNodeId, userId);
        } catch (Exception e) {
            log.error("[ContentExtractionApplicationService] 内容提取失败: fileNodeId={}", fileNodeId, e);
        }
    }

    /**
     * 提取文件内容并更新搜索索引
     */
    public void extractAndIndex(String fileNodeId, String userId) {
        FileNode fileNode = fileNodeRepository.findById(fileNodeId);
        if (fileNode == null || !fileNode.isFile()) {
            return;
        }

        String suffix = fileNode.getSuffix();
        if (suffix == null || suffix.isEmpty()) {
            log.debug("[ContentExtractionApplicationService] 无后缀，跳过: fileNodeId={}", fileNodeId);
            return;
        }

        String content = null;
        if (TEXT_SUFFIXES.contains(suffix.toLowerCase())) {
            content = extractTextContent(fileNode);
        }
        // PDF/Office 等二进制文档需要集成 Tika（可选）
        // if ("pdf".equals(suffix)) content = extractByTika(fileNode);
        // if (OFFICE_SUFFIXES.contains(suffix)) content = extractByTika(fileNode);

        if (content != null && !content.isEmpty()) {
            // 限制最大长度
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH);
            }
            searchDomainService.indexFile(fileNodeId, content, userId);
            log.info("[ContentExtractionApplicationService] 内容索引完成: fileNodeId={}, contentLength={}",
                    fileNodeId, content.length());
        } else {
            // 无内容提取时仍需索引文件元数据
            searchDomainService.indexFile(fileNodeId, null, userId);
        }
    }

    /**
     * 从纯文本文件中提取内容
     */
    private String extractTextContent(FileNode fileNode) {
        IFileStorage storage = resolveStorage();
        if (storage == null) {
            return null;
        }
        try (InputStream is = storage.downloadAsStream(fileNode.getBucketName(), fileNode.getStorageKey())) {
            byte[] bytes = is.readNBytes(MAX_CONTENT_LENGTH + 1);
            if (bytes.length > MAX_CONTENT_LENGTH) {
                bytes = java.util.Arrays.copyOf(bytes, MAX_CONTENT_LENGTH);
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            String suffix = fileNode.getSuffix();
            if (suffix != null) {
                suffix = suffix.toLowerCase();
                if ("html".equals(suffix) || "htm".equals(suffix)) {
                    content = stripHtmlTags(content);
                }
            }
            return content;
        } catch (Exception e) {
            log.warn("[ContentExtractionApplicationService] 文本提取失败: fileNodeId={}, error={}",
                    fileNode.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * P2-R1: HTML 标签清理增强（先移除 script/style 块再清理标签）
     */
    private String stripHtmlTags(String html) {
        // 先移除 script 和 style 块（含内容）
        String result = html;
        result = result.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        result = result.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        // 移除 HTML 注释
        result = result.replaceAll("(?is)<!--.*?-->", " ");
        // 移除 CDATA 块
        result = result.replaceAll("(?is)<!\\[CDATA\\[.*?\\]\\]>", " ");
        // 移除所有 HTML 标签
        result = result.replaceAll("<[^>]+>", " ");
        // 解码常见 HTML 实体
        result = result.replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&nbsp;", " ");
        // 压缩连续空白
        result = result.replaceAll("\\s+", " ").trim();
        return result;
    }

    private IFileStorage resolveStorage() {
        if (fileStorageProvider != null) {
            return fileStorageProvider.getStorage();
        }
        return null;
    }
}
