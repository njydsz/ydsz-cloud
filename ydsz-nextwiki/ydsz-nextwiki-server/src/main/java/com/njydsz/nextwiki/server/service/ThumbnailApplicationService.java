package com.njydsz.nextwiki.server.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import javax.imageio.ImageIO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.config.NextwikiProperties;
import com.njydsz.nextwiki.server.converter.NextwikiConverter;

/**
 * 缩略图服务。
 *
 * <p>生成图片/PDF/Office 缩略图。
 *
 * <p>多尺寸输出。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailApplicationService {

  private final FileNodeRepository fileNodeRepository;
  private final NextwikiProperties properties;

  @Autowired(required = false)
  private IFileStorageProvider fileStorageProvider;

  /** 缩略图尺寸：小图边长（像素） */
  public static final int SIZE_SMALL = 64;

  /** 缩略图尺寸：中图边长（像素），当前默认生成尺寸 */
  public static final int SIZE_MEDIUM = 128;

  /** 缩略图尺寸：大图边长（像素） */
  public static final int SIZE_LARGE = 256;

  /** 支持缩略图生成的图片后缀 */
  private static final Set<String> IMAGE_SUFFIXES =
      Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

  /**
   * 异步生成缩略图（由 {@code nextwikiTaskExecutor} 线程池执行）。
   *
   * <p>内部捕获全部异常仅记日志，不阻塞主流程；真正逻辑见 {@link #generateThumbnail}。
   *
   * @param fileNodeId 文件节点 ID
   * @concurrency 异步执行；异常被吞掉仅告警
   * @note 本方法无事务边界
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
   * 生成缩略图并上传到存储（P2-2 修复：实际缩放生成而非仅占位）。
   *
   * <p>仅对图片类型（{@code IMAGE_SUFFIXES}）做真实缩放，输出为 PNG 上传至 {@code
   * wiki/thumbnail/{fileNodeId}_thumb.png} 并回填 {@code thumbnailKey}； 非图片类型仅预置
   * key（缩略图后续可由预览服务补充）。存储未配置时退化为仅写 key。
   *
   * @param fileNodeId 文件节点 ID
   * @throws IOException 下载/缩放/上传过程中的 IO 或图像读取异常（仅异步入口吞掉，同步调用会向上抛）
   * @complexity O(imagePixels)（图片解码 + 双线性缩放 + 编码上传）
   * @concurrency 无共享可变状态，可并发；同一文件并发生成以最后写入为准
   * @note 方法结束在 {@code finally} 清理原图临时文件；缩略图临时文件在成功后删除
   */
  public void generateThumbnail(String fileNodeId) throws IOException {
    FileNodeVO node = fileNodeRepository.findById(fileNodeId).orElse(null);
    if (node == null || !node.isFile()) {
      return;
    }

    String suffix = node.getSuffix();
    if (suffix == null) {
      return;
    }
    suffix = suffix.toLowerCase();

    String thumbnailKey = "wiki/thumbnail/" + fileNodeId + "_thumb.png";

    // 仅对图片类型生成实际缩略图
    if (IMAGE_SUFFIXES.contains(suffix)) {
      IFileStorage storage = resolveStorage();
      if (storage == null) {
        node.setThumbnailKey(thumbnailKey);
        fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(node));
        return;
      }

      // 下载原图到临时文件
      Path tempFile =
          Path.of(properties.getThumbnail().getTempDir(), fileNodeId + "_orig." + suffix);
      Files.createDirectories(tempFile.getParent());
      try (InputStream is =
          storage.downloadAsStream(node.getBucketName(), node.getStorageKey())) {
        Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
      }

      try {
        // 生成缩略图
        BufferedImage thumbnail =
            generateThumbnailImage(Files.newInputStream(tempFile), SIZE_MEDIUM);

        // 写入临时文件并上传到存储
        Path thumbFile = Path.of(properties.getThumbnail().getTempDir(), fileNodeId + "_thumb.png");
        ImageIO.write(thumbnail, "png", thumbFile.toFile());

        // 上传到存储
        MultipartFile multipartFile =
            new PathMultipartFile(thumbFile, fileNodeId + "_thumb.png", "image/png");
        storage.upload(null, thumbnailKey, multipartFile);

        node.setThumbnailKey(thumbnailKey);
        fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(node));
        log.info("[ThumbnailApplicationService] 缩略图生成并上传完成: fileNodeId={}", fileNodeId);

        Files.deleteIfExists(thumbFile);
      } finally {
        Files.deleteIfExists(tempFile);
      }
    } else {
      // 非图片类型仅设置 key（后续可由预览服务填充）
      node.setThumbnailKey(thumbnailKey);
      fileNodeRepository.update(NextwikiConverter.INSTANT.toDTO(node));
    }
  }

  private IFileStorage resolveStorage() {
    if (fileStorageProvider != null) {
      return fileStorageProvider.getStorage();
    }
    return null;
  }

  /** 简单的 Path → MultipartFile 实现 */
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

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getOriginalFilename() {
      return name;
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

  /**
   * 将图片 InputStream 等比缩放为边长不超过 {@code targetSize} 的缩略图（保持宽高比，双线性插值）。
   *
   * @param inputStream 原图输入流（方法内读取，调用方负责关闭）
   * @param targetSize 目标边长上限（像素），取宽高缩放比的较小值以保证完整可见
   * @return 缩放后的 {@link BufferedImage}（RGB 类型）
   * @throws IllegalArgumentException 图像无法解码（{@code ImageIO.read} 返回 null）时抛出
   * @throws IOException 图像 IO 异常
   * @complexity O(originalPixels)（一次解码 + 一次绘制缩放）
   * @note 纯计算，无副作用；线程安全
   */
  public BufferedImage generateThumbnailImage(InputStream inputStream, int targetSize)
      throws IOException {
    BufferedImage original = ImageIO.read(inputStream);
    if (original == null) {
      throw new IllegalArgumentException("无法读取图片");
    }

    int originalWidth = original.getWidth();
    int originalHeight = original.getHeight();

    // 计算缩放比例（保持宽高比）
    double scale =
        Math.min((double) targetSize / originalWidth, (double) targetSize / originalHeight);

    int scaledWidth = (int) (originalWidth * scale);
    int scaledHeight = (int) (originalHeight * scale);

    BufferedImage thumbnail =
        new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = thumbnail.createGraphics();
    g2d.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.drawImage(original, 0, 0, scaledWidth, scaledHeight, null);
    g2d.dispose();

    return thumbnail;
  }
}
