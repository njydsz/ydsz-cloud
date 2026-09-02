package com.njydsz.common.docs.watermark;

/**
 * PDF 水印叠加能力抽象。
 *
 * <p>为 PDF 文档叠加防泄露水印（用户名 + 时间等文本），是 common-docs 暴露给业务模块的
 * 能力边界。业务模块必须通过本接口使用 PDF 水印能力，禁止直接依赖 PDFBox 等第三方 SDK。
 *
 * <p><b>实现约定：</b>
 *
 * <ul>
 *   <li>能力提供者实现（如 {@code PdfBoxWatermarkApplier}）内部封装第三方 PDF 库，本模块负责隔离</li>
 *   <li>实现类须配合 {@code @ConditionalOnClass} 控制装配，运行时无对应依赖时业务方可降级跳过</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface PdfWatermarkApplier {

  /**
   * 为 PDF 文档叠加文字水印。
   *
   * <p>对输入 PDF 的每一页叠加半透明文字水印（旋转排版），返回叠加后的新 PDF 字节流，
   * 不修改输入字节。
   *
   * @param pdfBytes 原始 PDF 字节，不可为空
   * @param watermarkText 水印文本，为空时返回原文件字节
   * @return 叠加水印后的 PDF 字节；水印文本为空时返回原始字节
   * @throws com.njydsz.common.docs.exception.DocumentException 水印叠加失败（非 PDF 数据 / IO 异常）
   */
  byte[] applyWatermark(byte[] pdfBytes, String watermarkText);
}
