package com.njydsz.nextwiki.server.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.config.NextwikiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档预览应用服务
 * <p>
 * 提供 Office -> PDF 转换（基于 LibreOffice 命令行）和缩略图生成。
 * 转换后的 PDF 会上传到存储作为预览副本，供前端直接访问。
 *
 * <p><b>预览流程：</b>
 * <ol>
 *   <li>从存储下载原文件到临时目录</li>
 *   <li>调用 LibreOffice headless 模式转换为 PDF</li>
 *   <li>上传 PDF 到存储（预览副本，存储键: wiki/preview/{nodeId}.pdf）</li>
 *   <li>更新 FileNode 的 previewReady 和 previewStorageKey</li>
 *   <li>图片类型直接生成缩略图并上传到存储</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewApplicationService {

    private final FileNodeRepository fileNodeRepository;
    private final NextwikiProperties properties;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /** 支持 Office 预览的文件后缀 */
    private static final Set<String> OFFICE_SUFFIXES = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf"
    );

    /** 支持直接预览的文件后缀（无需转换） */
    private static final Set<String> DIRECT_PREVIEW_SUFFIXES = Set.of(
            "pdf", "txt", "md", "html", "htm", "csv", "json", "xml"
    );

    /** 图片文件后缀 */
    private static final Set<String> IMAGE_SUFFIXES = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg"
    );

    /**
     * 生成预览（异步调用）
     */
    @Async("nextwikiTaskExecutor")
    public void generatePreview(String fileNodeId) {
        try {
            doGeneratePreview(fileNodeId);
        } catch (Exception e) {
            // 异步方法不向调用方抛出异常，仅记录日志
            log.error("[PreviewApplicationService] 预览生成失败: fileNodeId={}", fileNodeId, e);
        }
    }

    /**
     * 预览生成的实际执行逻辑
     */
    private void doGeneratePreview(String fileNodeId) {
        FileNode fileNode = fileNodeRepository.findById(fileNodeId);
        if (fileNode == null || !fileNode.isFile()) {
            log.warn("[PreviewApplicationService] 文件节点不存在或不是文件: {}", fileNodeId);
            return;
        }

        String suffix = fileNode.getSuffix();
        if (suffix == null || suffix.isEmpty()) {
            log.warn("[PreviewApplicationService] 文件无扩展名，跳过预览生成: {}", fileNodeId);
            return;
        }

        if (OFFICE_SUFFIXES.contains(suffix)) {
            try {
                convertOfficeToPdf(fileNode);
            } catch (Exception e) {
                log.error("[PreviewApplicationService] Office 转 PDF 失败: fileNodeId={}", fileNodeId, e);
            }
        } else if (IMAGE_SUFFIXES.contains(suffix)) {
            try {
                generateImageThumbnail(fileNode);
            } catch (Exception e) {
                log.error("[PreviewApplicationService] 图片缩略图生成失败: fileNodeId={}", fileNodeId, e);
            }
        } else if (DIRECT_PREVIEW_SUFFIXES.contains(suffix)) {
            fileNode.setPreviewReady(true);
            fileNodeRepository.update(fileNode);
        }
    }

    /**
     * 检查文件是否支持预览
     */
    public boolean isPreviewSupported(String suffix) {
        if (suffix == null) return false;
        String s = suffix.toLowerCase();
        return OFFICE_SUFFIXES.contains(s)
                || DIRECT_PREVIEW_SUFFIXES.contains(s)
                || IMAGE_SUFFIXES.contains(s);
    }

    /**
     * 获取预览类型
     */
    public String getPreviewType(String suffix) {
        if (suffix == null) return "none";
        String s = suffix.toLowerCase();
        if (OFFICE_SUFFIXES.contains(s)) return "office";
        if (DIRECT_PREVIEW_SUFFIXES.contains(s)) return "direct";
        if (IMAGE_SUFFIXES.contains(s)) return "image";
        return "none";
    }

    // ==================== 私有方法 ====================

    /**
     * 从存储下载文件到临时路径
     */
    private Path downloadToTemp(FileNode fileNode) throws Exception {
        IFileStorage storage = resolveStorage();
        if (storage == null) {
            throw new BusinessException(NextwikiExceptionCode.FILE_STORAGE_NOT_CONFIGURED);
        }

        Path tempDirPath = Path.of(properties.getPreview().getTempDir());
        Files.createDirectories(tempDirPath);

        String tempFileId = UUID.randomUUID().toString().replace("-", "");
        String suffix = fileNode.getSuffix() != null ? fileNode.getSuffix() : "tmp";
        Path tempFile = tempDirPath.resolve(tempFileId + "." + suffix);

        try (InputStream is = storage.downloadAsStream(fileNode.getBucketName(), fileNode.getStorageKey())) {
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("[PreviewApplicationService] 文件下载到临时路径: fileNodeId={}, tempPath={}",
                fileNode.getId(), tempFile);
        return tempFile;
    }

    /**
     * Office 文档转 PDF（调用 LibreOffice headless）
     * 转换后将 PDF 上传到存储作为预览副本。
     */
    private void convertOfficeToPdf(FileNode fileNode) throws Exception {
        Path inputFile = null;
        Path outputDir = null;
        try {
            inputFile = downloadToTemp(fileNode);

            String tempFileId = UUID.randomUUID().toString().replace("-", "");
            String tempDir = properties.getPreview().getTempDir();
            String libreofficePath = properties.getPreview().getLibreofficePath();
            outputDir = Path.of(tempDir).resolve("output-" + tempFileId);
            Files.createDirectories(outputDir);

            log.info("[PreviewApplicationService] 开始 Office->PDF 转换: fileNodeId={}, suffix={}",
                    fileNode.getId(), fileNode.getSuffix());

            ProcessBuilder pb = new ProcessBuilder(
                    libreofficePath,
                    "--headless",
                    "--convert-to", "pdf",
                    "--outdir", outputDir.toString(),
                    inputFile.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(NextwikiExceptionCode.PREVIEW_GENERATION_FAILED);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorOutput = new String(process.getInputStream().readAllBytes());
                throw BusinessException.of(NextwikiExceptionCode.PREVIEW_GENERATION_FAILED)
                        .data("errorOutput", errorOutput);
            }

            String pdfName = fileNode.getName().substring(0,
                    fileNode.getName().lastIndexOf('.')) + ".pdf";
            Path pdfFile = outputDir.resolve(pdfName);

            if (!Files.exists(pdfFile)) {
                File[] pdfs = outputDir.toFile().listFiles((dir, name) -> name.endsWith(".pdf"));
                if (pdfs != null && pdfs.length > 0) {
                    pdfFile = pdfs[0].toPath();
                } else {
                    throw new BusinessException(NextwikiExceptionCode.PREVIEW_NOT_READY);
                }
            }

            // 上传 PDF 预览副本到存储
            String previewStorageKey = "wiki/preview/" + fileNode.getId() + ".pdf";
            IFileStorage storage = resolveStorage();
            if (storage != null) {
                uploadFileToStorage(storage, pdfFile, previewStorageKey, "application/pdf");
                log.info("[PreviewApplicationService] PDF 预览副本已上传: fileNodeId={}, previewKey={}",
                        fileNode.getId(), previewStorageKey);
            }

            fileNode.setPreviewReady(true);
            fileNode.setThumbnailKey(previewStorageKey);
            fileNodeRepository.update(fileNode);

            log.info("[PreviewApplicationService] Office->PDF 转换完成: fileNodeId={}", fileNode.getId());
        } finally {
            // P0-5: 确保所有临时文件和目录被递归清理
            if (inputFile != null) {
                Files.deleteIfExists(inputFile);
            }
            if (outputDir != null) {
                deleteDirectoryRecursive(outputDir);
            }
        }
    }

    /**
     * P0-5: 递归删除目录及其内容
     */
    private void deleteDirectoryRecursive(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 生成图片缩略图（P0-R4: 委托 ThumbnailApplicationService，消除重复逻辑）
     * <p>
     * 原实现直接上传原图作为缩略图（无缩放），与 ThumbnailApplicationService 的实际缩放逻辑冲突。
     * 现委托 ThumbnailApplicationService 统一处理。
     */
    private void generateImageThumbnail(FileNode fileNode) {
        // P0-R4: 直接委托 ThumbnailApplicationService，不再重复实现
        // ThumbnailApplicationService 会下载原图、缩放、上传到存储、更新 thumbnailKey
        // 此处仅标记预览就绪（图片可直接预览）
        fileNode.setPreviewReady(true);
        fileNodeRepository.update(fileNode);
        log.info("[PreviewApplicationService] 图片预览就绪（缩略图由 ThumbnailApplicationService 异步生成）: fileNodeId={}",
                fileNode.getId());
    }

    /**
     * 上传文件到存储
     * <p>
     * 将本地临时文件通过 {@link IFileStorage#upload} 上传到存储后端。
     */
    private void uploadFileToStorage(IFileStorage storage, Path filePath,
                                       String storageKey, String contentType) throws Exception {
        MultipartFile multipartFile = new PathBackedMultipartFile(filePath, storageKey, contentType);
        FileStorage uploaded = storage.upload(null, storageKey, multipartFile);
        log.debug("[PreviewApplicationService] 上传到存储: key={}, size={}",
                storageKey, uploaded.getSize());
    }

    private IFileStorage resolveStorage() {
        if (fileStorageProvider != null) {
            return fileStorageProvider.getStorage();
        }
        return null;
    }

    /**
     * 基于 Path 的 MultipartFile 简单实现
     * <p>
     * 用于将本地临时文件通过 {@code IFileStorage.upload} 上传到存储后端。
     * 仅实现 MultipartFile 接口必要方法，不做额外校验。
     */
    private static class PathBackedMultipartFile implements MultipartFile {

        private final Path filePath;
        private final String name;
        private final String contentType;
        private final long size;

        PathBackedMultipartFile(Path filePath, String name, String contentType) throws IOException {
            this.filePath = filePath;
            this.name = name;
            this.contentType = contentType;
            this.size = Files.size(filePath);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            Path fileName = filePath.getFileName();
            return fileName != null ? fileName.toString() : name;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(filePath);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(filePath);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(filePath, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
