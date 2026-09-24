package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OCR 光学字符识别配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.ocr}，控制基于视觉模型的图片/PDF 文档文字识别能力。
 * 配置项包括提供商、视觉模型、PDF 渲染 DPI、超时时间与失败降级策略。
 * 默认不开启（isEnabled=false），使用 LLM 视觉模型识别，失败时自动降级。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrProperties {
  private static final int DEFAULT_TIMEOUT_SECONDS = 60;
  private static final int DEFAULT_PDF_DPI = 200;

  private boolean isEnabled = false;
  private String provider = "llm-vision";
  private String visionModel = "qwen-vl-max";
  private int pdfDpi = DEFAULT_PDF_DPI;
  private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
  private boolean isDegradedOnFailure = true;
}
