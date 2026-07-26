package com.njydsz.nextwiki.server.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.nextwiki.domain.vo.FileNodeVO;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 批量导入应用服务
 * <p>
 * 支持批量文件上传和从压缩包导入。
 *
 * <p><b>功能：</b>
 * <ul>
 *   <li>批量文件上传（并发处理，限制并发数）</li>
 *   <li>ZIP 压缩包解压导入（保持目录结构）</li>
 *   <li>导入进度追踪</li>
 *   <li>线程池生命周期管理（优雅关闭）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchImportApplicationService {

    private final FileApplicationService fileApplicationService;

    /** P0-1: 并发上传线程池（由 ydsz-common-thread 统一管理，配置项: ydsz.thread.pools.nextwikiBatchImport） */
    @Resource(name = "nextwikiBatchImportExecutor")
    private java.util.concurrent.Executor batchImportExecutor;

    /** 最大批量上传数量 */
    private static final int MAX_BATCH_SIZE = 100;

    /** ZIP 最大解压文件数 */
    private static final int MAX_ZIP_ENTRIES = 500;

    /** 单个 ZIP 条目最大大小（50MB） */
    private static final long MAX_ENTRY_SIZE = 50L * 1024 * 1024;

    /** ZIP 炸弹防护：总解压大小上限（500MB） */
    private static final long MAX_TOTAL_UNCOMPRESSED = 500L * 1024 * 1024;

    /**
     * 批量上传文件
     */
    public BatchImportResult batchUpload(MultipartFile[] files, String parentId, String userId) {
        if (files == null || files.length == 0) {
            return BatchImportResult.empty();
        }

        if (files.length > MAX_BATCH_SIZE) {
            return BatchImportResult.error("批量上传数量超过限制: " + MAX_BATCH_SIZE);
        }

        List<CompletableFuture<FileNodeVO>> futures = new ArrayList<>();
        for (MultipartFile file : files) {
            CompletableFuture<FileNodeVO> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return fileApplicationService.upload(file, parentId, null, null, userId);
                } catch (Exception e) {
                    log.error("[BatchImportApplicationService] 文件上传失败: {}",
                            file.getOriginalFilename(), e);
                    return null;
                }
            }, batchImportExecutor);
            futures.add(future);
        }

        List<FileNodeVO> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        int failed = files.length - results.size();
        log.info("[BatchImportApplicationService] 批量上传完成: total={}, success={}, failed={}",
                files.length, results.size(), failed);

        return BatchImportResult.success(results, files.length, results.size(), failed);
    }

    /**
     * 从 ZIP 压缩包导入
     * <p>
     * 解压 ZIP 文件并保持目录结构，递归创建目录和上传文件。
     * 包含 ZIP 炸弹防护（限制条目数和总解压大小）。
     */
    public BatchImportResult importFromZip(MultipartFile zipFile, String parentId, String userId) {
        if (zipFile == null || zipFile.isEmpty()) {
            return BatchImportResult.error("ZIP 文件为空");
        }

        log.info("[BatchImportApplicationService] ZIP 导入: fileName={}, parentId={}",
                zipFile.getOriginalFilename(), parentId);

        List<FileNodeVO> importedFiles = new ArrayList<>();
        int totalCount = 0;
        int failedCount = 0;
        long totalUncompressed = 0;

        try (InputStream is = zipFile.getInputStream();
             ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // ZIP 炸弹防护
                if (++totalCount > MAX_ZIP_ENTRIES) {
                    log.warn("[BatchImportApplicationService] ZIP 条目数超过限制: {}", MAX_ZIP_ENTRIES);
                    break;
                }

                // 路径穿越防护：跳过包含 ".." 的可疑条目
                String entryPath = entry.getName();
                if (entryPath.contains("..")) {
                    log.warn("[BatchImportApplicationService] 跳过可疑 ZIP 条目: {}", entryPath);
                    continue;
                }

                if (entry.isDirectory()) {
                    String folderName = extractFolderName(entry.getName());
                    if (folderName != null && !folderName.isEmpty()) {
                        try {
                            fileApplicationService.createFolder(parentId, folderName, userId);
                        } catch (Exception e) {
                            log.warn("[BatchImportApplicationService] 创建目录失败: {}",
                                    folderName, e);
                        }
                    }
                    continue;
                }

                // 读取条目内容到内存（受 MAX_ENTRY_SIZE 限制）
                Path tempFile = Files.createTempFile("nextwiki-zip-", ".tmp");
                try {
                    long bytesCopied = Files.copy(zis, tempFile, StandardCopyOption.REPLACE_EXISTING);

                    if (bytesCopied > MAX_ENTRY_SIZE) {
                        log.warn("[BatchImportApplicationService] 条目大小超过限制: {} ({}bytes)",
                                entry.getName(), bytesCopied);
                        failedCount++;
                        continue;
                    }

                    totalUncompressed += bytesCopied;
                    if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED) {
                        log.warn("[BatchImportApplicationService] 总解压大小超过限制: {}MB",
                                MAX_TOTAL_UNCOMPRESSED / 1024 / 1024);
                        break;
                    }

                    String fileName = extractFileName(entry.getName());
                    if (fileName.isEmpty()) {
                        continue;
                    }

                    // 将临时文件包装为 MultipartFile 并上传
                    byte[] content = Files.readAllBytes(tempFile);
                    String contentType = Files.probeContentType(tempFile);
                    InMemoryMultipartFile multipartFile = new InMemoryMultipartFile(
                            "file", fileName, contentType != null ? contentType : "application/octet-stream", content);

                    FileNodeVO uploaded = fileApplicationService.upload(
                            multipartFile, parentId, fileName, "ZIP导入", userId);
                    if (uploaded != null) {
                        importedFiles.add(uploaded);
                    }
                } catch (Exception e) {
                    log.error("[BatchImportApplicationService] ZIP 条目导入失败: {}",
                            entry.getName(), e);
                    failedCount++;
                } finally {
                    Files.deleteIfExists(tempFile);
                }
            }
        } catch (IOException e) {
            log.error("[BatchImportApplicationService] ZIP 解压失败", e);
            return BatchImportResult.error("ZIP 解压失败: " + e.getMessage());
        }

        log.info("[BatchImportApplicationService] ZIP 导入完成: total={}, success={}, failed={}",
                totalCount, importedFiles.size(), failedCount);

        return BatchImportResult.success(importedFiles, totalCount, importedFiles.size(), failedCount);
    }

    // ==================== 私有方法 ====================

    private String extractFileName(String entryPath) {
        if (entryPath == null || entryPath.isEmpty()) {
            return "";
        }
        int lastSlash = entryPath.lastIndexOf('/');
        return lastSlash >= 0 ? entryPath.substring(lastSlash + 1) : entryPath;
    }

    private String extractFolderName(String entryPath) {
        if (entryPath == null || entryPath.isEmpty()) {
            return null;
        }
        String trimmed = entryPath.endsWith("/")
                ? entryPath.substring(0, entryPath.length() - 1)
                : entryPath;
        int lastSlash = trimmed.lastIndexOf('/');
        return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
    }

    // ==================== 内部类 ====================

    /**
     * 基于内存字节数组的 MultipartFile 实现
     * <p>
     * 用于将 ZIP 解压后的文件内容传递给 FileApplicationService.upload。
     */
    private static class InMemoryMultipartFile implements MultipartFile {

        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String name, String originalFilename,
                               String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content != null ? content.length : 0;
        }

        @Override
        public byte[] getBytes() {
            return content != null ? content : new byte[0];
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content != null ? content : new byte[0]);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            try (OutputStream os = Files.newOutputStream(dest.toPath())) {
                if (content != null) {
                    os.write(content);
                }
            }
        }

        @Override
        public void transferTo(Path dest) throws IOException {
            if (content != null) {
                Files.write(dest, content);
            }
        }
    }

    /**
     * 批量导入结果
     */
    @Data
    @Builder
    public static class BatchImportResult {
        private boolean success;
        private String message;
        private List<FileNodeVO> importedFiles;
        private int totalCount;
        private int successCount;
        private int failedCount;

        public static BatchImportResult success(List<FileNodeVO> files, int total,
                                                  int success, int failed) {
            return BatchImportResult.builder()
                    .success(true)
                    .importedFiles(files)
                    .totalCount(total)
                    .successCount(success)
                    .failedCount(failed)
                    .build();
        }

        public static BatchImportResult error(String message) {
            return BatchImportResult.builder()
                    .success(false)
                    .message(message)
                    .build();
        }

        public static BatchImportResult empty() {
            return BatchImportResult.builder()
                    .success(true)
                    .importedFiles(List.of())
                    .totalCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .build();
        }
    }
}
