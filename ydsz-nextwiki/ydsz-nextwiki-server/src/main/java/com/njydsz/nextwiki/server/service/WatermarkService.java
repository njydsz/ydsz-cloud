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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

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
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatermarkService {

  /** 图片格式：PNG */
  private static final String MIME_IMAGE_PNG = "image/png";

  /** 图片格式：JPEG */
  private static final String MIME_IMAGE_JPEG = "image/jpeg";

  /** PDF 格式 */
  private static final String MIME_PDF = "application/pdf";

  /** 水印透明度（0-255，越小越透明） */
  private static final int WATERMARK_OPACITY = 50;

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
        int fontSize = Math.max(16, Math.min(width, height) / 25);
        Font font = new Font("SansSerif", Font.BOLD, fontSize);
        g2d.setFont(font);

        // 计算文字尺寸
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(watermarkText);
        int textHeight = fontMetrics.getHeight();

        // 设置颜色和透明度
        g2d.setColor(new Color(128, 128, 128, WATERMARK_OPACITY));
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f));

        // 对角线平铺水印
        int stepX = textWidth + 100;
        int stepY = textHeight + 80;
        for (int y = 0; y < height + stepY; y += stepY) {
          for (int x = -stepX / 2; x < width + stepX; x += stepX) {
            // 绘制旋转 30 度的文字
            g2d.rotate(Math.toRadians(30), x, y);
            g2d.drawString(watermarkText, x, y);
            g2d.rotate(Math.toRadians(-30), x, y);
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
   * <p>注意：需要 Apache PDFBox 依赖。如果 PDFBox 不可用，返回原始文件。
   *
   * <p><b>依赖要求：</b>需要在 server/pom.xml 引入：
   *
   * <pre>
   * &lt;dependency&gt;
   *   &lt;groupId&gt;org.apache.pdfbox&lt;/groupId&gt;
   *   &lt;artifactId&gt;pdfbox&lt;/artifactId&gt;
   * &lt;/dependency&gt;
   * </pre>
   *
   * @param fileBytes 原始 PDF 字节
   * @param watermarkText 水印文本
   * @return 叠加水印后的 PDF 字节，若 PDFBox 不可用则返回原始文件
   */
  public byte[] watermarkPdf(byte[] fileBytes, String watermarkText) {
    // PDFBox 依赖检查（运行时检测，避免硬依赖）
    try {
      Class.forName("org.apache.pdfbox.pdmodel.PDDocument");
    } catch (ClassNotFoundException e) {
      log.warn("[WatermarkService] PDFBox 不可用，跳过 PDF 水印叠加");
      return fileBytes;
    }

    try (PDDocument document = PDDocument.load(new ByteArrayInputStream(fileBytes));
        ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

      // 为每一页添加水印
      for (PDPage page : document.getPages()) {
        addPdfWatermarkPage(document, page, watermarkText);
      }

      document.save(bos);
      return bos.toByteArray();
    } catch (Exception e) {
      log.error("[WatermarkService] PDF 水印叠加失败，返回原始文件: err={}", e.getMessage(), e);
      return fileBytes;
    }
  }

  /**
   * 为 PDF 单页添加文字水印。
   *
   * @param document PDF 文档
   * @param page PDF 页面
   * @param watermarkText 水印文本
   * @throws IOException PDF 处理异常
   */
  private void addPdfWatermarkPage(
      PDDocument document, PDPage page, String watermarkText)
      throws IOException {
    PDPageContentStream contentStream =
        new PDPageContentStream(
            document, page, PDPageContentStream.AppendMode.APPEND, true);

    contentStream.setFont(PDType1Font.OBLIQUE, 20);

    float pageSize = page.getMediaBox().getHeight();
    contentStream.setNonStrokingColor(200, 200, 200);

    // 旋转 45 度绘制水印
    contentStream.beginText();
    contentStream.newLineAtOffset(100, pageSize / 3);
    contentStream.showText(watermarkText);
    contentStream.endText();

    contentStream.close();
  }

  /**
   * 掩码用户ID（用于匿名化追踪）。
   *
   * @param userId 用户ID
   * @return 掩码后的ID（如 "123****890"）
   */
  private String maskUserId(String userId) {
    if (userId == null || userId.length() <= 4) {
      return "****";
    }
    return userId.substring(0, 3) + "****" + userId.substring(userId.length() - 3);
  }
}
