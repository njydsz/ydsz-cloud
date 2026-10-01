package com.njydsz.agent.infra.ocr;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.ocr.ImageFormat;
import com.njydsz.agent.domain.ocr.OcrService;
import com.njydsz.common.docs.ocr.OcrEngine;

/**
 * OCR 引擎适配器 —— 将 Agent 自身的 {@link OcrService} 能力桥接为 common-docs {@link OcrEngine} SPI。
 *
 * <p>使 common-docs {@code OcrProvider.ocrScan()} 能够通过标准 SPI 调用 Agent 的 LLM Vision OCR 识别能力，
 * 实现 OCR 引擎接口统一（P1-2 整改项）。
 *
 * <p>适配器仅负责接口转换，实际 OCR 识别逻辑仍由 {@link OcrService} 实现完成。
 *
 * <p><b>注意：</b>Agent 的 {@link OcrService#recognize(byte[], ImageFormat)} 不感知页码，
 * {@link OcrEngine#recognize(byte[], int)} 的 {@code pageNumber} 参数在此适配器中被忽略。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see OcrEngine
 * @see OcrService
 */
@Component
public class OcrEngineAdapter implements OcrEngine {

  private final OcrService ocrService;

  public OcrEngineAdapter(OcrService ocrService) {
    this.ocrService = ocrService;
  }

  @Override
  public String recognize(byte[] imageBytes, int pageNumber) {
    return ocrService.recognize(imageBytes, ImageFormat.PNG);
  }

  @Override
  public String getName() {
    return "agent-llm-vision-ocr";
  }
}
