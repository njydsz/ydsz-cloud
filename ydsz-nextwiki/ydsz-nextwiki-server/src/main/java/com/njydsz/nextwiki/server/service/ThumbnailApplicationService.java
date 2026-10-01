package com.njydsz.nextwiki.server.service;

import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.njydsz.common.file.spi.ImageProcessor;
import com.njydsz.common.file.storage.IFileStorage;
import com.njydsz.common.file.storage.IFileStorageProvider;
import com.njydsz.common.file.util.FileOps;
import com.njydsz.common.locales.util.I18n;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.server.config.NextwikiProperties;

/**
 * 缩略图服务。
 *
 * <p>生成图片/PDF/Office 缩略图。
 *
 * <p>多尺寸输出。
 *
 * <p><b>算法说明：</b>缩略图缩放涉及图像几何计算，使用 primitive double 是图形学领域标准实践（ImageIO / ImageMagick / OpenCV 均使用 double）。
 * 不涉及金额/比例等精确业务值，故保留 double 类型。
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
  private final NextwikiStructMapper mapper;

  @Autowired(required = false)
  private IFileStorageProvider fileStorageProvider;

  /** 图片处理 SPI（默认 AWT 实现，可替换为 libvips/ImageMagick 高性能后端） */
  @Autowired(required = false)
  private ImageProcessor imageProcessor;

  /** 缩略图尺寸：小图边长（像素） */
  public static final int SIZE_SMALL = 64;

  /** 缩略图尺寸：中图边长（像素），当前默认生成尺寸 */
  public static final int SIZE_MEDIUM = 128;

  /** 缩略图尺寸：大图边长（像素） */
  public static final int SIZE_LARGE = 256;

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
   * <p>仅对图片类型（{@link FileOps#IMAGE_SUFFIXES}）做真实缩放，输出为 PNG 上传至 {@code
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
    if (FileOps.IMAGE_SUFFIXES.contains(suffix)) {
      IFileStorage storage = resolveStorage();
      if (storage == null) {
        node.setThumbnailKey(thumbnailKey);
        fileNodeRepository.update(mapper.fileNodeVOToDTO(node));
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
        // 生成缩略图（委托 ImageProcessor SPI，默认 AWT 实现，可替换为 libvips/ImageMagick）
        if (imageProcessor == null) {
          throw new IllegalStateException(I18n.message("nextwiki.error.image_processor_not_available"));
        }
        Path thumbFile = Path.of(properties.getThumbnail().getTempDir(), fileNodeId + "_thumb.png");
        try (InputStream srcIs = Files.newInputStream(tempFile);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
          Dimension targetDim = new Dimension(SIZE_MEDIUM, SIZE_MEDIUM);
          imageProcessor.scale(srcIs, bos, targetDim, true);
          Files.write(thumbFile, bos.toByteArray());
        }

        // 上传到存储（委托 FileOps.toMultipartFile，避免重复实现 Path→MultipartFile 适配）
        storage.upload(null, thumbnailKey,
            FileOps.toMultipartFile(thumbFile, fileNodeId + "_thumb.png", "image/png"));

        node.setThumbnailKey(thumbnailKey);
        fileNodeRepository.update(mapper.fileNodeVOToDTO(node));
        log.info("[ThumbnailApplicationService] 缩略图生成并上传完成: fileNodeId={}", fileNodeId);

        Files.deleteIfExists(thumbFile);
      } finally {
        Files.deleteIfExists(tempFile);
      }
    } else {
      // 非图片类型仅设置 key（后续可由预览服务填充）
      node.setThumbnailKey(thumbnailKey);
      fileNodeRepository.update(mapper.fileNodeVOToDTO(node));
    }
  }

  private IFileStorage resolveStorage() {
    if (fileStorageProvider != null) {
      return fileStorageProvider.getStorage();
    }
    return null;
  }

}
