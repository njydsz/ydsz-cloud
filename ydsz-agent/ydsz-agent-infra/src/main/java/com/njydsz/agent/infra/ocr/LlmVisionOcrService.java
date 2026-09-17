package com.njydsz.agent.infra.ocr;

import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.config.properties.OcrProperties;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.MessageContent;
import com.njydsz.agent.domain.ocr.ImageFormat;
import com.njydsz.agent.domain.ocr.OcrService;

/**
 * 基于多模态 LLM 的 OCR 服务实现
 *
 * <p>将图片 base64 编码后发给多模态 LLM（如 qwen-vl）提取文字。
 * 依赖 {@link LlmClient} 接口，通过配置指定 Vision 模型。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Component
public class LlmVisionOcrService implements OcrService {

  /** 默认温度：OCR 任务需要精确提取文字，使用低温度 */
  private static final double OCR_TEMPERATURE = 0.1;

  /** OCR 任务默认最大输出 Token */
  private static final int OCR_MAX_TOKENS = 2048;

  /** Base64 内联图片前缀 */
  private static final String DATA_IMAGE_PREFIX = "data:%s;base64,%s";

  private final LlmClient llmClient;
  private final AgentProperties properties;

  public LlmVisionOcrService(LlmClient llmClient, AgentProperties properties) {
    this.llmClient = llmClient;
    this.properties = properties;
  }

  @Override
  public String recognize(byte[] imageBytes, ImageFormat format) {
    if (imageBytes == null || imageBytes.length == 0) {
      return "";
    }

    String imageDataUrl = buildDataUrl(imageBytes, format);
    String visionModel = properties.getOcr().getVisionModel();
    String promptText = "请仔细识别图片中的所有文字内容，按原文顺序完整输出，不要添加任何解释或额外描述。";

    ChatRequest request = ChatRequest.builder()
        .model(visionModel)
        .messages(List.of(
            ChatMessage.system("你是一个专业的 OCR 文字识别助手，能够准确识别图片中的文字内容。"),
            ChatMessage.userWithContent(
                MessageContent.textAndImage(promptText, imageDataUrl), null)))
        .temperature(OCR_TEMPERATURE)
        .maxTokens(OCR_MAX_TOKENS)
        .build();

    ChatResponse response = llmClient.chat(request);
    String content = response.getContent();
    return content != null ? content.trim() : "";
  }

  @Override
  public boolean isAvailable() {
    OcrProperties ocrConfig = properties.getOcr();
    if (!ocrConfig.isEnabled()) {
      return false;
    }
    return llmClient.supports(ocrConfig.getVisionModel());
  }

  /**
   * 构建 Base64 内联图片 URL
   *
   * @param imageBytes 图片字节
   * @param format 图片格式
   * @return data:image/...;base64,... 格式的 URL
   */
  private String buildDataUrl(byte[] imageBytes, ImageFormat format) {
    String base64 = Base64.getEncoder().encodeToString(imageBytes);
    String mimeType = format != null ? format.getMimeType() : "image/png";
    return String.format(DATA_IMAGE_PREFIX, mimeType, base64);
  }
}
