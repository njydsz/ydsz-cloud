package com.njydsz.common.file.spi;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * 默认图片处理器实现（基于 Java AWT + ImageIO + TwelveMonkeys SPI）。
 *
 * <p>利用 JDK 内置能力完成常见图片操作，无需额外 native 依赖。
 * 支持的格式取决于运行时 classpath（基础 JDK 支持 JPEG/PNG/BMP/GIF，
 * 引入 com.twelvemonkeys.imageio 后扩展至 TIFF/WEBP/ICO）。
 *
 * <p>性能适中（单张 5MB 图片处理 &lt; 200ms），满足常见业务场景；
 * 大量 / 高吞吐场景建议业务模块用 libvips / ImageMagick 后端覆盖。
 *
 * @author ydsz-team
 * @since 26.09.25
 * @see ImageProcessor
 */
public class DefaultImageProcessor implements ImageProcessor {

  private static final String DEFAULT_FORMAT = "JPEG";

  /** 平铺模式最小字号（像素） */
  private static final int TILED_MIN_FONT_SIZE = 16;

  /** 字号按图片短边缩放的分母 */
  private static final int TILED_FONT_SIZE_DIVISOR = 25;

  /** 平铺水印灰度 RGB 分量值 */
  private static final int TILED_GRAY_RGB = 128;

  /** 平铺水印透明度（AlphaComposite 原始值 0-255） */
  private static final int TILED_OPACITY_RAW = 50;

  /** 平铺水平步长附加间距（像素） */
  private static final int TILED_SPACING_X = 100;

  /** 平铺垂直步长附加间距（像素） */
  private static final int TILED_SPACING_Y = 80;

  /** 平铺旋转角度（度） */
  private static final double TILED_ROTATE_DEGREES = 30;

  @Override
  public void scale(
      InputStream input,
      java.io.OutputStream output,
      java.awt.Dimension targetSize,
      boolean keepAspectRatio)
      throws IOException {
    BufferedImage src = ImageIO.read(input);
    if (src == null) {
      throw new IOException("无法识别图片格式或输入流为空");
    }
    int targetW = targetSize.width;
    int targetH = targetSize.height;
    if (keepAspectRatio) {
      double ratio =
          Math.min((double) targetW / src.getWidth(), (double) targetH / src.getHeight());
      targetW = (int) (src.getWidth() * ratio);
      targetH = (int) (src.getHeight() * ratio);
    }
    BufferedImage scaled =
        new BufferedImage(
            Math.max(1, targetW), Math.max(1, targetH), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = scaled.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(src.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH), 0, 0, null);
    g.dispose();
    ImageIO.write(scaled, DEFAULT_FORMAT, output);
  }

  @Override
  public void crop(
      InputStream input, java.io.OutputStream output, int x, int y, int width, int height)
      throws IOException {
    BufferedImage src = ImageIO.read(input);
    if (src == null) {
      throw new IOException("无法识别图片格式或输入流为空");
    }
    int clampedX = Math.max(0, x);
    int clampedY = Math.max(0, y);
    int clampedW = Math.min(width, src.getWidth() - clampedX);
    int clampedH = Math.min(height, src.getHeight() - clampedY);
    if (clampedW <= 0 || clampedH <= 0) {
      throw new IOException(
          String.format("裁剪区域无效: x=%d, y=%d, w=%d, h=%d", x, y, width, height));
    }
    BufferedImage cropped = src.getSubimage(clampedX, clampedY, clampedW, clampedH);
    ImageIO.write(cropped, DEFAULT_FORMAT, output);
  }

  @Override
  public void watermark(
      InputStream input,
      java.io.OutputStream output,
      String watermarkText,
      float opacity,
      WatermarkPosition position)
      throws IOException {
    BufferedImage src = ImageIO.read(input);
    if (src == null) {
      throw new IOException("无法识别图片格式或输入流为空");
    }
    if (position == WatermarkPosition.TILED) {
      applyTiledWatermark(src, watermarkText, opacity);
    } else {
      applySingleWatermark(src, watermarkText, opacity, position);
    }
    ImageIO.write(src, DEFAULT_FORMAT, output);
  }

  /**
   * 对角线平铺水印（防截屏/拍照溯源模式）。
   *
   * <p>字号根据图片短边等比缩放（最小 {@value TILED_MIN_FONT_SIZE}px），按步长矩阵覆盖全图，
   * 每个水印单元旋转 {@value TILED_ROTATE_DEGREES}°。
   *
   * @param src 源图片（原地绘制）
   * @param watermarkText 水印文本
   * @param opacity 不透明度（0.0 ~ 1.0）
   */
  private void applyTiledWatermark(BufferedImage src, String watermarkText, float opacity) {
    Graphics2D g = src.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int fontSize =
          Math.max(
              TILED_MIN_FONT_SIZE,
              Math.min(src.getWidth(), src.getHeight()) / TILED_FONT_SIZE_DIVISOR);
      Font font = new Font("SansSerif", Font.BOLD, fontSize);
      g.setFont(font);
      FontMetrics fontMetrics = g.getFontMetrics();
      int textWidth = fontMetrics.stringWidth(watermarkText);
      int textHeight = fontMetrics.getHeight();
      g.setColor(new Color(TILED_GRAY_RGB, TILED_GRAY_RGB, TILED_GRAY_RGB, TILED_OPACITY_RAW));
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
      int stepX = textWidth + TILED_SPACING_X;
      int stepY = textHeight + TILED_SPACING_Y;
      for (int y = 0; y < src.getHeight() + stepY; y += stepY) {
        for (int x = -stepX / 2; x < src.getWidth() + stepX; x += stepX) {
          g.rotate(Math.toRadians(TILED_ROTATE_DEGREES), x, y);
          g.drawString(watermarkText, x, y);
          g.rotate(Math.toRadians(-TILED_ROTATE_DEGREES), x, y);
        }
      }
    } finally {
      g.dispose();
    }
  }

  /**
   * 单位置水印模式。
   *
   * @param src 源图片（原地绘制）
   * @param watermarkText 水印文本
   * @param opacity 不透明度（0.0 ~ 1.0）
   * @param position 水印位置
   */
  private void applySingleWatermark(
      BufferedImage src, String watermarkText, float opacity, WatermarkPosition position) {
    Graphics2D g = src.createGraphics();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
    g.setColor(Color.GRAY);
    g.setFont(new Font("SansSerif", Font.BOLD, 24));
    int textWidth = g.getFontMetrics().stringWidth(watermarkText);
    int textHeight = g.getFontMetrics().getHeight();
    int x = 10;
    int y = textHeight;
    switch (position) {
      case TOP_RIGHT -> x = src.getWidth() - textWidth - 10;
      case BOTTOM_LEFT -> y = src.getHeight() - 10;
      case BOTTOM_RIGHT -> {
        x = src.getWidth() - textWidth - 10;
        y = src.getHeight() - 10;
      }
      case CENTER -> {
        x = (src.getWidth() - textWidth) / 2;
        y = (src.getHeight() + textHeight) / 2;
      }
      default -> {
        /* TOP_LEFT: defaults */
      }
    }
    g.drawString(watermarkText, x, y);
    g.dispose();
  }

  @Override
  public void compress(InputStream input, java.io.OutputStream output, float quality)
      throws IOException {
    BufferedImage src = ImageIO.read(input);
    if (src == null) {
      throw new IOException("无法识别图片格式或输入流为空");
    }
    javax.imageio.plugins.jpeg.JPEGImageWriteParam jpegParams =
        new javax.imageio.plugins.jpeg.JPEGImageWriteParam(null);
    jpegParams.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
    jpegParams.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, quality)));
    javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("JPEG").next();
    try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(output)) {
      writer.setOutput(ios);
      writer.write(null, new javax.imageio.IIOImage(src, null, null), jpegParams);
    } finally {
      writer.dispose();
    }
  }

  @Override
  public void convert(InputStream input, java.io.OutputStream output, String targetFormat)
      throws IOException {
    BufferedImage src = ImageIO.read(input);
    if (src == null) {
      throw new IOException("无法识别图片格式或输入流为空");
    }
    boolean success = ImageIO.write(src, targetFormat, output);
    if (!success) {
      throw new IOException("不支持转换为格式: " + targetFormat);
    }
  }

  @Override
  public ImageMetadata getMetadata(InputStream input) throws IOException {
    try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
      if (!readers.hasNext()) {
        throw new IOException("无法识别图片格式");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(iis, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        String format = reader.getFormatName();
        return new ImageMetadata(width, height, format, -1);
      } finally {
        reader.dispose();
      }
    }
  }
}
