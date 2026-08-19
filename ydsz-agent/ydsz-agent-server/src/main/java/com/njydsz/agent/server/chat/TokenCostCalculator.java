package com.njydsz.agent.server.chat;

import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.CostEstimate;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.rag.TokenEstimator;

/**
 * Token 预计算与成本核算组件
 *
 * <p>在 LLM 调用前基于字符数估算 Token 用量与成本（用于配额预检、前端展示）； 在调用后基于实际 {@link TokenUsage} 精确核算（用于计费、记录）。
 *
 * <p>估算策略：对 messages 列表中每条消息的 content 求字符数，除以 {@code tokenCharRatio} 得到估算 Token 数。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class TokenCostCalculator {

  /** 默认字符系数（中英混合） */
  private static final double DEFAULT_CHAR_RATIO = 2.5;

  /** 未知模型兜底单价（USD / 千 Token） */
  private static final double FALLBACK_PRICE = 0.001;

  private final AgentProperties properties;

  public TokenCostCalculator(AgentProperties properties) {
    this.properties = properties;
  }

  /**
   * 调用前估算 Token 用量与成本。
   *
   * @param request LLM 请求
   * @return 成本估算值对象
   */
  public CostEstimate estimateBeforeCall(ChatRequest request) {
    List<ChatMessage> messages = request.getMessages();
    int totalChars = 0;
    for (ChatMessage message : messages) {
      String content = message.getContent();
      if (content != null) {
        totalChars += content.length();
      }
    }
    double charRatio = properties.getMemory().getTokenCharRatio();
    // P1 修复：TokenEstimator.estimate 接受 String 文本并按内部 length 估算；
    // 此处已累加字符数，直接按字符数/系数估算，避免将 int 误传为 String 参数
    int estimatedPromptTokens = Math.max(1, (int) Math.ceil(totalChars / charRatio));
    double unitPrice = resolveUnitPrice(request.getModel());
    return CostEstimate.estimate(
        estimatedPromptTokens, request.getMaxTokens(), request.getModel(), unitPrice);
  }

  /**
   * 调用后基于实际用量精确核算成本。
   *
   * @param usage 实际 Token 用量
   * @param model 模型名称
   * @return 实际成本核算值对象
   */
  public CostEstimate calculateActual(TokenUsage usage, String model) {
    double unitPrice = resolveUnitPrice(model);
    return CostEstimate.actual(usage, model, unitPrice);
  }

  /**
   * 根据模型名称解析单价（USD / 千 Token）。
   *
   * @param model 模型名称
   * @return 模型单价，未匹配时返回兜底价
   */
  private double resolveUnitPrice(String model) {
    if (model == null || model.isBlank()) {
      return FALLBACK_PRICE;
    }
    AgentProperties.Llm llm = properties.getLlm();
    if (llm.getModelPrices() != null && llm.getModelPrices().containsKey(model)) {
      return llm.getModelPrices().get(model);
    }
    return estimatePriceByModelPrefix(model);
  }

  /**
   * 基于模型名前缀估算单价（兜底策略）。
   *
   * <p>当用户未在 {@code ydsz.agent.llm.model-prices} 中配置价格时，按模型名前缀匹配默认单价。
   *
   * @param model 模型名称
   * @return 估算单价
   */
  private double estimatePriceByModelPrefix(String model) {
    String lower = model.toLowerCase();
    if (lower.contains("gpt-4o-mini")) {
      return 0.00015;
    }
    if (lower.contains("gpt-4o")) {
      return 0.0025;
    }
    if (lower.contains("gpt-4-turbo")) {
      return 0.01;
    }
    if (lower.contains("gpt-3.5")) {
      return 0.0005;
    }
    if (lower.contains("deepseek")) {
      return 0.00014;
    }
    return FALLBACK_PRICE;
  }
}
