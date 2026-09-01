package com.njydsz.common.docs.ocr;

/**
 * OCR 引擎接口
 *
 * <p>业务方实现此接口对接外部 OCR 服务（如阿里云 OCR / 百度 OCR / Tesseract）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface OcrEngine {

  /**
   * 识别图片中的文字
   *
   * @param imageBytes 图片字节流（PNG 格式）
   * @param pageNumber 页码
   * @return 识别到的文本
   */
  String recognize(byte[] imageBytes, int pageNumber);

  /**
   * 获取 OCR 引擎名称
   *
   * @return 引擎名称
   */
  String getName();
}
