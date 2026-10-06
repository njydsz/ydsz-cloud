package com.njydsz.nextwiki.server.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.njydsz.common.docs.watermark.PdfWatermarkApplier;
import com.njydsz.common.file.spi.ImageProcessor;
import com.njydsz.common.file.spi.ImageProcessor.WatermarkPosition;
import com.njydsz.common.util.date.DateUtils;
import com.njydsz.common.util.mask.MaskUtils;

/**
 * 文件水印服务
 *
 * <p><b>S3-P2-03：文件水印功能</b>
 *
 * <p>在文件下载/预览时动态叠加水印（用户名 + 时间），防止截屏/拍照泄露。
 *
 * <p><b>说明：</b>图片水印委托 common-file {@link ImageProcessor}，业务层不直接操作图形库。
 * 不透明度使用 primitive float 是图形学领域标准实践（与 AWT / OpenCV 一致），不涉及金额/比例精确业务值。
 *
 * <p><b>支持的文件类型：</b>
 *
 * <ul>
 *   <li>PDF：叠加文字水印</li>
 *   <li>图片（PNG/JPG）：叠加文字水印</li>
 *   <li>其他类型：暂无水印（直接返回原始文件）</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>下载机密文件时强制叠加水印</li>
 *   <li>预览敏感文件时显示水印</li>
 *   <li>分享链接下载时叠加访问者信息</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
public class WatermarkService {

  /** PDF 水印能力提供者（common-docs 封装 PDFBox），运行时无 PDFBox 依赖时为 null */
  private final ObjectProvider<PdfWatermarkApplier> pdfWatermarkApplierProvider;

  /** 图片水印能力（common-file 统一图片处理器） */
  private final ImageProcessor imageProcessor;

  /**
   * 构造水印服务。
   *
   * @param pdfWatermarkApplierProvider PDF 水印能力提供者（可选，无实现时跳过 PDF 水印）
   * @param imageProcessor 图片处理能力（common-file 封装）
   */
  public WatermarkService(
      ObjectProvider<PdfWatermarkApplier> pdfWatermarkApplierProvider,
      ImageProcessor imageProcessor) {
    this.pdfWatermarkApplierProvider = pdfWatermarkApplierProvider;
    this.imageProcessor = imageProcessor;
  }

  /** 图片格式：PNG */
  private static final String MIME_IMAGE_PNG = "image/png";

  /** 图片格式：JPEG */
  private static final String MIME_IMAGE_JPEG = "image/jpeg";

  /** PDF 格式 */
  private static final String MIME_PDF = "application/pdf";

  /** 图片水印不透明度（委托 common-file ImageProcessor 的平铺模式） */
  private static final float WATERMARK_ALPHA = 0.2f;

  /** 用户 ID 掩码：保留前 3 位 */
  private static final int MASK_ID_KEEP_CHARS = 3;

  /** 用户 ID 掩码最小长度 */
  private static final int MASK_ID_MIN_LENGTH = 4;

  /**
   * 判断文件格式是否支持水印叠加。
   *
   * @param mimeType MIME 类型
   * @return {@code true} 表示支持水印
   */
  public boolean isWatermarkSupported(String mimeType) {
    if (mimeType == null) {
      return false;
    }
    return MIME_IMAGE_PNG.equals(mimeType)
        || MIME_IMAGE_JPEG.equals(mimeType)
        || MIME_PDF.equals(mimeType);
  }

  /**
   * 获取文件的水印文本。
   *
   * <p>包含用户名、时间、租户标识等信息。
   *
   * @param userName 用户名
   * @param userId 用户ID（用于匿名化追踪）
   * @return 水印文本
   */
  public String getWatermarkText(String userName, String userId) {
    String timeStr = DateUtils.now();
    if (userName != null && !userName.isEmpty()) {
      return userName + " " + timeStr;
    }
    return "ID:" + maskUserId(userId) + " " + timeStr;
  }

  /**
   * 为图片文件叠加水印。
   *
   * <p>委托 common-file {@link ImageProcessor#watermark} 的平铺模式（{@link WatermarkPosition#TILED}），
   * 将水印文字以对角线矩阵倾斜覆盖全图，实现防截屏/拍照溯源保护。
   *
   * @param fileBytes 原始文件字节
   * @param mimeType MIME 类型（仅支持 PNG/JPEG）
   * @param watermarkText 水印文本
   * @return 叠加水印后的文件字节
   * @throws IOException 图片处理异常
   */
  public byte[] watermarkImage(byte[] fileBytes, String mimeType, String watermarkText)
      throws IOException {
    if (!MIME_IMAGE_PNG.equals(mimeType) && !MIME_IMAGE_JPEG.equals(mimeType)) {
      log.warn("[WatermarkService] 的图片格式不支持水印: {}", mimeType);
      return fileBytes;
    }

    try (InputStream is = new ByteArrayInputStream(fileBytes);
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      imageProcessor.watermark(is, bos, watermarkText, WATERMARK_ALPHA, WatermarkPosition.TILED);
      return bos.toByteArray();
    } catch (Exception e) {
      log.error("[WatermarkService] 叠加水印失败，返回原始文件: err={}", e.getMessage(), e);
      return fileBytes;
    }
  }

  /**
   * 为 PDF 文件叠加水印。
   *
   * <p>委托 ydsz-common-docs 的 {@link PdfWatermarkApplier} 能力实现，业务层不直接依赖
   * PDFBox 等第三方 SDK。若 common-docs 未装配 PDF 水印能力（运行时无 PDFBox 依赖），
   * 或叠加失败，返回原始文件。
   *
   * @param fileBytes 原始 PDF 字节
   * @param watermarkText 水印文本
   * @return 叠加水印后的 PDF 字节，能力不可用或叠加失败时返回原始文件
   */
  public byte[] watermarkPdf(byte[] fileBytes, String watermarkText) {
    PdfWatermarkApplier applier = pdfWatermarkApplierProvider.getIfAvailable();
    if (applier == null) {
      log.warn("[WatermarkService] PDF 水印能力不可用（common-docs 未装配 PdfWatermarkApplier），跳过 PDF 水印叠加");
      return fileBytes;
    }
    try {
      return applier.applyWatermark(fileBytes, watermarkText);
    } catch (Exception e) {
      log.error("[WatermarkService] PDF 水印叠加失败，返回原始文件: err={}", e.getMessage(), e);
      return fileBytes;
    }
  }

  /**
   * 掩码用户ID（用于匿名化追踪）。
   *
   * @param userId 用户ID
   * @return 掩码后的ID（如 "123****890"）
   */
  private String maskUserId(String userId) {
    if (userId == null || userId.length() <= MASK_ID_MIN_LENGTH) {
      return MaskUtils.PLACEHOLDER;
    }
    return MaskUtils.mask(userId, MASK_ID_KEEP_CHARS, MASK_ID_KEEP_CHARS);
  }
}
