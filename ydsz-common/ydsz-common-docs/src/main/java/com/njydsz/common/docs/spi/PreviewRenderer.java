package com.njydsz.common.docs.spi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件预览渲染器 SPI。
 *
 * <p>定义 Office/PDF 文档 → 图片预览的转换能力，使业务模块能够：
 * <ul>
 *   <li>将 PDF 文档每页渲染为 PNG/JPEG 图片序列（文档预览）</li>
 *   <li>将 Word/Excel/PPT 文档渲染为图片（需 LibreOffice / JODConverter 后端）</li>
 *   <li>生成多分辨率缩略图（原图/中图/小图）适配不同终端</li>
 * </ul>
 *
 * <p>分层：
 * <table border="1">
 *   <tr><th>格式</th><th>底层依赖</th><th>输出</th></tr>
 *   <tr><td>PDF</td><td>PDFBox 3.x</td><td>PNG 序列</td></tr>
 *   <tr><td>Word (.docx/.doc)</td><td>LibreOffice + JODConverter</td><td>PNG 序列</td></tr>
 *   <tr><td>Excel (.xlsx)</td><td>LibreOffice + JODConverter</td><td>PNG 序列</td></tr>
 *   <tr><td>PPT (.pptx)</td><td>LibreOffice + JODConverter</td><td>PNG 序列</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Service
 * public class PreviewService {
 *     private final PreviewRenderer previewRenderer;
 *     public List<byte> previewPdf(byte[] pdf) throws IOException {
 *         try (InputStream in = new ByteArrayInputStream(pdf);
 *              ByteArrayOutputStream out = new ByteArrayOutputStream()) {
 *             List<PreviewRenderer.PageImage> pages = previewRenderer.renderPdfToImages(in, "PNG");
 *             return pages.stream().map(PreviewRenderer.PageImage::data).toList();
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 * @see TemplateEngine
 */
public interface PreviewRenderer {

  /**
   * 将 PDF 文档渲染为图片序列（每页一张）。
   *
   * @param pdfStream PDF 输入流（非 null；调用方负责关闭）
   * @param outputFormat 输出图片格式（"PNG" / "JPEG"）
   * @return 按页码排序的图片列表
   * @throws IOException PDF 解析失败或渲染失败
   */
  java.util.List<PageImage> renderPdfToImages(InputStream pdfStream, String outputFormat)
      throws IOException;

  /**
   * 将 Office 文档（Word / Excel / PPT）渲染为图片序列。
   *
   * <p>需要本地安装 LibreOffice 并配置 soffice 可执行文件路径（{@code jodconverter.local.home}）。
   * 环境未配置时应抛出 {@link UnsupportedOperationException} 而非静默失败。
   *
   * @param officeStream Office 文档输入流（非 null；调用方负责关闭）
   * @param outputFormat 输出图片格式（"PNG" / "JPEG"）
   * @return 按页码排序的图片列表
   * @throws IOException 文档解析失败或渲染失败
   * @throws UnsupportedOperationException 环境未安装 LibreOffice
   */
  java.util.List<PageImage> renderOfficeToImages(InputStream officeStream, String outputFormat)
      throws IOException;

  /**
   * 单页预览图片。
   *
   * @param pageNumber 页码（从 1 开始）
   * @param width 图片宽度（像素）
   * @param height 图片高度（像素）
   * @param format 图片格式
   * @param data 图片字节数据
   */
  record PageImage(int pageNumber, int width, int height, String format, byte[] data) {}
}
