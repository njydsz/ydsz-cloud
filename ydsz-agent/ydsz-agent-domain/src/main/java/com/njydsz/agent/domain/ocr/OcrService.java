package com.njydsz.agent.domain.ocr;

/**
 * OCR 服务接口
 *
 * <p>将扫描版 PDF / 图片中的文字提取为可索引文本。
 * 支持多 Provider 路由（内部 LLM Vision / Tesseract / 云服务商 OCR）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface OcrService {

  /**
   * OCR 识别
   *
   * @param imageBytes 图片字节
   * @param format 图片格式（PNG/JPEG）
   * @return 识别的文本内容
   */
  String recognize(byte[] imageBytes, ImageFormat format);

  /**
   * 判断当前 OCR 引擎是否可用
   *
   * @return true 表示引擎可用
   */
  boolean isAvailable();
}
