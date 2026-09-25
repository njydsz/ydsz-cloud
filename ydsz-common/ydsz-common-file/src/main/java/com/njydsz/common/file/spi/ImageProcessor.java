package com.njydsz.common.file.spi;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 统一图片处理 SPI。
 *
 * <p>定义图片处理的基础操作集合，对标 Hutool Img / Thumbnailator / imgscalr 的能力矩阵，
 * 使业务模块以编程式 API 完成常见图片操作，避免直接依赖底层图像库（Java AWT / TwelveMonkeys / ImageIO）。
 *
 * <p>能力分层：
 * <table border="1">
 *   <tr><th>操作</th><th>方法</th><th>典型场景</th></tr>
 *   <tr><td>缩放</td><td>{@link #scale}</td><td>缩略图生成</td></tr>
 *   <tr><td>裁剪</td><td>{@link #crop}</td><td>头像裁切</td></tr>
 *   <tr><td>水印</td><td>{@link #watermark}</td><td>版权保护</td></tr>
 *   <tr><td>压缩</td><td>{@link #compress}</td><td>Web 传输瘦身</td></tr>
 *   <tr><td>格式转换</td><td>{@link #convert}</td><td>PNG → JPEG</td></tr>
 *   <tr><td>元数据</td><td>{@link #getMetadata}</td><td>EXIF 解析</td></tr>
 * </table>
 *
 * <p><b>默认实现</b>：ydsz-common-file 提供 {@code DefaultImageProcessor}（基于 Java AWT + ImageIO）；
 * 业务模块可使用 libvips / ImageMagick 后端覆盖以获得更高性能。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Service
 * public class AvatarService {
 *     private final ImageProcessor imageProcessor;
 *     public byte[] createThumbnail(byte[] avatar, int width, int height) throws IOException {
 *         try (InputStream in = new ByteArrayInputStream(avatar);
 *              ByteArrayOutputStream out = new ByteArrayOutputStream()) {
 *             imageProcessor.scale(in, out, new Dimension(width, height), true);
 *             return out.toByteArray();
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public interface ImageProcessor {

  /**
   * 按比例缩放图片。
   *
   * @param input 输入流（非 null；调用方负责关闭）
   * @param output 输出流（非 null；调用方负责关闭）
   * @param targetSize 目标尺寸（宽 × 高）
   * @param keepAspectRatio true 保持宽高比（targetSize 为上限），false 强制拉伸
   * @throws IOException 读取/写入失败或格式不支持
   */
  void scale(InputStream input, OutputStream output, Dimension targetSize, boolean keepAspectRatio)
      throws IOException;

  /**
   * 从指定坐标裁剪图片区域。
   *
   * @param input 输入流
   * @param output 输出流
   * @param x 起始 X 坐标
   * @param y 起始 Y 坐标
   * @param width 裁剪宽度
   * @param height 裁剪高度
   * @throws IOException 读取/写入失败或裁剪区域超出原图范围
   */
  void crop(InputStream input, OutputStream output, int x, int y, int width, int height)
      throws IOException;

  /**
   * 添加文字或图片水印。
   *
   * @param input 输入流（底图）
   * @param output 输出流
   * @param watermarkText 水印文本（watermarkImage 为空时生效）
   * @param opacity 不透明度（0.0 ~ 1.0）
   * @param position 水印位置（CENTER、TOP_LEFT、TOP_RIGHT、BOTTOM_LEFT、BOTTOM_RIGHT）
   * @throws IOException 读取/写入失败
   */
  void watermark(
      InputStream input,
      OutputStream output,
      String watermarkText,
      float opacity,
      WatermarkPosition position)
      throws IOException;

  /**
   * 压缩图片（通过降质减少文件体积）。
   *
   * @param input 输入流
   * @param output 输出流
   * @param quality 压缩质量（0.0 ~ 1.0；0.8 为常用 Web 值）
   * @throws IOException 读取/写入失败或目标格式不支持有损压缩
   */
  void compress(InputStream input, OutputStream output, float quality) throws IOException;

  /**
   * 转换图片格式。
   *
   * @param input 输入流（源格式）
   * @param output 输出流（目标格式）
   * @param targetFormat 目标格式名（"JPEG"、"PNG"、"WEBP"、"BMP"、"GIF"）
   * @throws IOException 读取/写入失败或目标格式不支持
   */
  void convert(InputStream input, OutputStream output, String targetFormat) throws IOException;

  /**
   * 获取图片元信息（尺寸 + 格式）。
   *
   * @param input 输入流
   * @return 图片元信息（非 null）
   * @throws IOException 读取失败或无法识别格式
   */
  ImageMetadata getMetadata(InputStream input) throws IOException;

  /** 水印位置枚举。 */
  enum WatermarkPosition {
    CENTER,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
  }

  /**
   * 图片元信息。
   *
   * @param width 像素宽度
   * @param height 像素高度
   * @param format 图片格式（JPEG / PNG / WEBP / BMP / GIF）
   * @param sizeBytes 文件字节大小
   */
  record ImageMetadata(int width, int height, String format, long sizeBytes) {}
}
