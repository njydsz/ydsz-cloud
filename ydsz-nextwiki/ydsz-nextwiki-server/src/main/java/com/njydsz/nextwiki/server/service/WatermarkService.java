package com.njydsz.nextwiki.server.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.njydsz.common.docs.watermark.PdfWatermarkApplier;

/**
 * 文件水印服务
 *
 * <p><b>S3-P2-03：文件水印功能</b>
 *
 * <p>在文件下载/预览时动态叠加水印（用户名 + 时间），防止截屏/拍照泄露。
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
 * @since 26.09.01
 */
@Slf4j
@Service
public class WatermarkService {

  /** PDF 水印能力提供者（common-docs 封装 PDFBox），运行时无 PDFBox 依赖时为 null */
  private final ObjectProvider<PdfWatermarkApplier> pdfWatermarkApplierProvider;

  /**
   * 构造水印服务。
   *
   * @param pdfWatermarkApplierProvider PDF 水印能力提供者（可选，无实现时跳过 PDF 水印）
   */
  public WatermarkService(ObjectProvider<PdfWatermarkApplier> pdfWatermarkApplierProvider) {
    this.pdfWatermarkApplierProvider = pdfWatermarkApplierProvider;
  }

  /** 图片格式：PNG */
  private static final String MIME_IMAGE_PNG = "image/png";

  /** 图片格式：JPEG */
  private static final String MIME_IMAGE_JPEG = "image/jpeg";

  /** PDF 格式 */
  private static final String MIME_PDF = "application/pdf";

  /** 水印透明度（0-255，越小越透明） */
  private static final int WATERMARK_OPACITY = 50;

  /** 水印字体最小字号（像素） */
  private static final int MIN_FONT_SIZE = 16;

  /** 字号按图片短边缩放的分母 */
  private static final int FONT_SIZE_DIVISOR = 25;

  /** 水印灰度 RGB 分量值 */
  private static final int WATERMARK_GRAY_RGB = 128;

  /** 水印透明度（AlphaComposite） */
  private static final float WATERMARK_ALPHA = 0.2f;

  /** 水印平铺水平步长附加间距（像素） */
  private static final int TILE_SPACING_X = 100;

  /** 水印平铺垂直步长附加间距（像素） */
  private static final int TILE_SPACING_Y = 80;

  /** 水印旋转角度（度） */
  private static final double WATERMARK_ROTATE_DEGREES = 30;

  /** 用户 ID 掩码：保留前 3 位 */
  private static final int MASK_ID_KEEP_CHARS = 3;

  /** 用户 ID 掩码最小长度 */
  private static final int MASK_ID_MIN_LENGTH = 4;

  /** 用户 ID 掩码占位符 */
  private static final String MASK_ID_PLACEHOLDER = "****";

  /** 水印时间格式 */
  private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
    String timeStr = LocalDateTime.now().format(DATETIME_FORMAT);
    if (userName != null && !userName.isEmpty()) {
      return userName + " " + timeStr;
    }
    return "ID:" + maskUserId(userId) + " " + timeStr;
  }

  /**
   * 为图片文件叠加水印。
   *
   * <p>使用 Java 2D API 在图片上绘制半透明文字水印（对角线平铺）。
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

    try (InputStream is = new ByteArrayInputStream(fileBytes)) {
      BufferedImage originalImage = ImageIO.read(is);
      if (originalImage == null) {
        log.warn("[WatermarkService] 读取图片失败，返回原始文件");
        return fileBytes;
      }

      int width = originalImage.getWidth();
      int height = originalImage.getHeight();

      // 创建绘图上下文
      Graphics2D g2d = originalImage.createGraphics();
      try {
        // 设置抗锯齿
        g2d.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 设置字体（根据图片大小调整）
        int fontSize = Math.max(MIN_FONT_SIZE, Math.min(width, height) / FONT_SIZE_DIVISOR);
        Font font = new Font("SansSerif", Font.BOLD, fontSize);
        g2d.setFont(font);

        // 计算文字尺寸
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(watermarkText);
        int textHeight = fontMetrics.getHeight();

        // 设置颜色和透明度
        g2d.setColor(
            new Color(
                WATERMARK_GRAY_RGB,
                WATERMARK_GRAY_RGB,
                WATERMARK_GRAY_RGB,
                WATERMARK_OPACITY));
        g2d.setComposite(
            AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WATERMARK_ALPHA));

        // 对角线平铺水印
        int stepX = textWidth + TILE_SPACING_X;
        int stepY = textHeight + TILE_SPACING_Y;
        for (int y = 0; y < height + stepY; y += stepY) {
          for (int x = -stepX / 2; x < width + stepX; x += stepX) {
            // 绘制旋转 30 度的文字
            g2d.rotate(Math.toRadians(WATERMARK_ROTATE_DEGREES), x, y);
            g2d.drawString(watermarkText, x, y);
            g2d.rotate(-Math.toRadians(WATERMARK_ROTATE_DEGREES), x, y);
          }
        }
      } finally {
        g2d.dispose();
      }

      // 输出为字节数组
      try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
        String formatName = MIME_IMAGE_PNG.equals(mimeType) ? "png" : "jpg";
        ImageIO.write(originalImage, formatName, bos);
        return bos.toByteArray();
      }
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
      return "****";
    }
    return userId.substring(0, MASK_ID_KEEP_CHARS)
        + MASK_ID_PLACEHOLDER
        + userId.substring(userId.length() - MASK_ID_KEEP_CHARS);
  }
}
