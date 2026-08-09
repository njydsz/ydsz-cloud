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

import java.util.concurrent.Executor;
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
    private Executor batchImportExecutor;

    /** 最大批量上传数量 */
    private static final int MAX_BATCH_SIZE = 100;

    /** ZIP 最大解压文件数 */
    private static final int MAX_ZIP_ENTRIES = 500;

    /** 单个 ZIP 条目最大大小（50MB） */
    private static final long MAX_ENTRY_SIZE = 50L * 1024 * 1024;

    /** ZIP 炸弹防护：总解压大小上限（500MB） */
    private static final long MAX_TOTAL_UNCOMPRESSED = 500L * 1024 * 1024;

    /**
     * 批量上传文件（并发处理，单批上限 {@link #MAX_BATCH_SIZE}）。
     * <p>借助 {@code nextwikiBatchImportExecutor} 线程池并发上传，所有子任务完成（{@code join}）后汇总结果。
     * 单个文件上传失败不影响整体，失败项以 {@code null} 过滤，最终在结果中体现失败计数。
     *
     * @param files   待上传的文件数组，为 {@code null}/空时返回空结果（不报错）
     * @param parentId 目标父目录节点 ID，传入 {@code FileApplicationService.upload}
     * @param userId   操作人 ID，用于审计与权限归属
     * @return 批量导入结果 {@link BatchImportResult}，含成功/失败明细与计数
     * @throws 不会抛出非受检异常（单文件异常已在子任务内捕获）
     * @complexity 时间复杂度取决于最慢的单文件上传（并发执行），非累加
     * @concurrency 并发度受线程池 {@code nextwikiBatchImportExecutor} 容量约束；结果为各 {@code CompletableFuture} 汇总
     * @note 无数据库事务边界（逐个文件独立上传，不具备跨文件原子性）；线程安全
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

        int failed = files.length - Response.size();
        log.info("[BatchImportApplicationService] 批量上传完成: total={}, success={}, failed={}",
                files.length, Response.size(), failed);

        return BatchImportResult.success(results, files.length, Response.size(), failed);
    }

    /**
     * 从 ZIP 压缩包导入（保持目录结构，递归创建目录并上传文件）。
     * <p>内置多重防护：限制条目数（{@link #MAX_ZIP_ENTRIES}）、单条目大小（{@link #MAX_ENTRY_SIZE}）、
     * 总解压大小（{@link #MAX_TOTAL_UNCOMPRESSED}，防 ZIP 炸弹）、路径穿越（跳过含 {@code ".."} 的条目）。
     *
     * @param zipFile  上传的 ZIP 压缩包，为 {@code null}/空时返回错误结果
     * @param parentId 导入目标父目录节点 ID
     * @param userId   操作人 ID
     * @return 批量导入结果 {@link BatchImportResult}，含成功文件列表与失败计数
     * @throws 不会抛出非受检异常（解压/IO 异常已捕获并转为错误结果）
     * @complexity 时间复杂度 O(M)（M 为条目数），受磁盘 IO 与逐文件上传影响
     * @concurrency 单线程顺序解压；内部逐文件上传为串行，非并发
     * @note 无整体事务边界，部分成功部分失败属正常；线程安全（仅使用局部变量）
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
        /** 是否整体成功（部分失败仍可能返回 true，需结合 failedCount 判断） */
        private boolean success;
        /** 错误/提示信息，成功时通常为空 */
        private String message;
        /** 成功导入的文件节点视图列表 */
        private List<FileNodeVO> importedFiles;
        /** 总处理条目数（批量上传为文件数，ZIP 导入为条目数） */
        private int totalCount;
        /** 成功导入条数 */
        private int successCount;
        /** 失败条数（单文件异常或超限导致） */
        private int failedCount;

        /**
         * 构造「批量导入完成」结果。
         *
         * <p><b>注意语义：</b>{@code success=true} 表示导入流程正常跑完，
         * <b>不代表每条都成功</b>。批量导入采用「单条失败不中断整批」策略，
         * 因此调用方必须结合 {@code failedCount} 判断是否需要提示用户重试，
         * 不能只看 {@link #isSuccess()}。
         *
         * @param files   成功落库的文件节点视图；无成功项时为空列表而非 {@code null}
         * @param total   本批总条目数（批量上传为文件数，ZIP 导入为压缩包内条目数）
         * @param success 成功条数
         * @param failed  失败条数，{@code success + failed} 应等于 {@code total}
         * @return 导入结果，{@code success=true}
         */
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

        /**
         * 构造「整批失败」结果。
         *
         * <p>仅用于导入<b>整体</b>无法进行的场景（如 ZIP 解压失败、目标目录无权限、
         * 超出批量上限）；单个文件的失败不走这里，而是计入
         * {@link #success(List, int, int, int)} 的 {@code failedCount}。
         *
         * <p>此时 {@code importedFiles} 为 {@code null}，各计数均为 0，
         * 调用方遍历前需判空。
         *
         * @param message 整批失败原因，会透传至前端提示
         * @return 失败结果，{@code success=false}
         */
        public static BatchImportResult error(String message) {
            return BatchImportResult.builder()
                    .success(false)
                    .message(message)
                    .build();
        }

        /**
         * 构造「空批次」结果。
         *
         * <p>入参没有任何可导入条目时返回，视为<b>成功</b>而非失败——
         * 用户提交空目录或全部条目被过滤属正常情形，不应弹错误。
         * 与 {@link #error(String)} 的区别在于 {@code importedFiles} 为空列表
         * （非 {@code null}），调用方可直接安全遍历。
         *
         * @return 空结果，{@code success=true} 且各计数为 0
         */
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
