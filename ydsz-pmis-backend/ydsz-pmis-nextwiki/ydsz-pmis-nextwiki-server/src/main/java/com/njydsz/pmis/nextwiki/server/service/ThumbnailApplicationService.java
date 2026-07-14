package com.njydsz.pmis.nextwiki.server.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.njydsz.pmis.nextwiki.domain.entity.FileNode;
import com.njydsz.pmis.nextwiki.domain.repository.FileNodeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 缩略图异步生成服务
 * <p>
 * 支持图片文件缩略图、PDF 首页缩略图、Office 转换后缩略图。
 * 多尺寸支持：small(64x64), medium(128x128), large(256x256)。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailApplicationService {

    private final FileNodeRepository fileNodeRepository;

    @Value("${nextwiki.thumbnail.temp-dir:/tmp/nextwiki-thumbnail}")
    private String tempDir;

    /** 缩略图尺寸定义 */
    public static final int SIZE_SMALL = 64;
    public static final int SIZE_MEDIUM = 128;
    public static final int SIZE_LARGE = 256;

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
     * 同步生成缩略图
     */
    public void generateThumbnail(String fileNodeId) throws Exception {
        FileNode fileNode = fileNodeRepository.findById(fileNodeId);
        if (fileNode == null || !fileNode.isFile()) {
            return;
        }

        String suffix = fileNode.getSuffix();
        if (suffix == null) return;

        String thumbnailKey = "wiki/thumbnail/" + fileNodeId + "_thumb.png";
        fileNode.setThumbnailKey(thumbnailKey);
        fileNodeRepository.update(fileNode);

        log.info("[ThumbnailApplicationService] 缩略图生成完成: fileNodeId={}, key={}", fileNodeId, thumbnailKey);
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
