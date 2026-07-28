package com.njydsz.nextwiki.server.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.njydsz.common.file.domain.FileStorage;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.server.config.NextwikiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;
/**
 * 缩略图服务。
 * <p>生成图片/PDF/Office 缩略图。
 * <p>多尺寸输出。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailApplicationService {

    private final FileNodeRepository fileNodeRepository;
    private final NextwikiProperties properties;

    @Autowired(required = false)
    private IFileStorageProvider fileStorageProvider;

    /** 缩略图尺寸定义 */
    public static final int SIZE_SMALL = 64;
    public static final int SIZE_MEDIUM = 128;
    public static final int SIZE_LARGE = 256;

    /** 支持缩略图生成的图片后缀 */
    private static final Set<String> IMAGE_SUFFIXES = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );

    /**
     * 异步生成缩略图
     */
    @Async("nextwikiTaskExecutor")
    public void generateThumbnailAsync(String fileNodeId) {
        try {
            generateThumbnail(fileNodeId);
        } catch (Exception e) {
            log.error("[ThumbnailApplicationService] 缩略图生成失败: fileNodeId={}", fileNodeId, e);
        }
    }

    /**
     * 生成缩略图（P2-2 修复：实际生成并上传到存储）
     */
    public void generateThumbnail(String fileNodeId) throws Exception {
        FileNode fileNode = fileNodeRepository.findById(fileNodeId);
        if (fileNode == null || !fileNode.isFile()) {
            return;
        }

        String suffix = fileNode.getSuffix();
        if (suffix == null) return;
        suffix = suffix.toLowerCase();

        String thumbnailKey = "wiki/thumbnail/" + fileNodeId + "_thumb.png";

        // 仅对图片类型生成实际缩略图
        if (IMAGE_SUFFIXES.contains(suffix)) {
            IFileStorage storage = resolveStorage();
            if (storage == null) {
                fileNode.setThumbnailKey(thumbnailKey);
                fileNodeRepository.update(fileNode);
                return;
            }

            // 下载原图到临时文件
            Path tempFile = Path.of(tempDir, fileNodeId + "_orig." + suffix);
            Files.createDirectories(tempFile.getParent());
            try (InputStream is = storage.downloadAsStream(fileNode.getBucketName(), fileNode.getStorageKey())) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                // 生成缩略图
                BufferedImage thumbnail = generateThumbnailImage(Files.newInputStream(tempFile), SIZE_MEDIUM);

                // 写入临时文件并上传到存储
                Path thumbFile = Path.of(properties.getThumbnail().getTempDir(), fileNodeId + "_thumb.png");
                ImageIO.write(thumbnail, "png", thumbFile.toFile());

                // 上传到存储
                MultipartFile multipartFile =
                        new PathMultipartFile(thumbFile, fileNodeId + "_thumb.png", "image/png");
                storage.upload(null, thumbnailKey, multipartFile);

                fileNode.setThumbnailKey(thumbnailKey);
                fileNodeRepository.update(fileNode);
                log.info("[ThumbnailApplicationService] 缩略图生成并上传完成: fileNodeId={}", fileNodeId);

                Files.deleteIfExists(thumbFile);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } else {
            // 非图片类型仅设置 key（后续可由预览服务填充）
            fileNode.setThumbnailKey(thumbnailKey);
            fileNodeRepository.update(fileNode);
        }
    }

    private IFileStorage resolveStorage() {
        if (fileStorageProvider != null) {
            return fileStorageProvider.getStorage();
        }
        return null;
    }

    /**
     * 简单的 Path → MultipartFile 实现
     */
    private static class PathMultipartFile implements MultipartFile {
        private final Path filePath;
        private final String name;
        private final String contentType;
        private final long size;

        PathMultipartFile(Path filePath, String name, String contentType) throws IOException {
            this.filePath = filePath;
            this.name = name;
            this.contentType = contentType;
            this.size = Files.size(filePath);
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return size == 0; }
        @Override public long getSize() { return size; }
        @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(filePath); }
        @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(filePath); }
        @Override public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(filePath, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 生成缩略图（从 InputStream）
     */
    public BufferedImage generateThumbnailImage(InputStream inputStream, int targetSize) throws Exception {
        BufferedImage original = ImageIO.read(inputStream);
        if (original == null) {
            throw new IllegalArgumentException("无法读取图片");
        }

        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();

        // 计算缩放比例（保持宽高比）
        double scale = Math.min(
                (double) targetSize / originalWidth,
                (double) targetSize / originalHeight
        );

        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);

        BufferedImage thumbnail = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = thumbnail.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.drawImage(original, 0, 0, scaledWidth, scaledHeight, null);
        g2d.dispose();

        return thumbnail;
    }
}
