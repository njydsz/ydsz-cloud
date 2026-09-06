package com.njydsz.agent.server.chat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.CostEstimate;
import com.njydsz.agent.domain.model.MessageContent;
import com.njydsz.agent.domain.model.TokenUsage;

/**
 * Token 预计算与成本核算组件
 *
 * <p>在 LLM 调用前基于字符数估算 Token 用量与成本（用于配额预检、前端展示）； 在调用后基于实际 {@link TokenUsage} 精确核算（用于计费、记录）。
 *
 * <p>估算策略：对 messages 列表中每条消息的 content 求字符数，除以 {@code tokenCharRatio} 得到估算 Token 数。
 *
 * <p>所有金额使用 {@link BigDecimal} 类型，精度 6 位小数（微美元级），符合货币计算规范。 单价来源于 {@link AgentProperties.Llm#getModelPrices()} 配置，兜底单价通过 {@code agent.llm.fallback-price} 配置项注入。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class TokenCostCalculator {

  /** 默认字符系数（中英混合） */
  private static final BigDecimal DEFAULT_CHAR_RATIO = new BigDecimal("2.5");

  /** 兜底单价除数（千 Token → 单 Token） */
  private static final BigDecimal DIVISOR_PER_THOUSAND = new BigDecimal("1000");

  private final AgentProperties properties;

  public TokenCostCalculator(AgentProperties properties) {
    this.properties = properties;
  }

  /**
   * 调用前估算 Token 用量与成本。
   *
   * <p>对纯文本消息按 content 字符数估算；对多模态消息按 {@link MessageContent#estimateTokenChars()} 估算（含图片固定 Token）。
   *
   * @param request LLM 请求
   * @return 成本估算值对象
   */
  public CostEstimate estimateBeforeCall(ChatRequest request) {
    List<ChatMessage> messages = request.getMessages();
    int totalChars = 0;
    for (ChatMessage message : messages) {
      // 多模态内容（Vision 模型）：按 MessageContent 估算 Token 字符数
      MessageContent multimodal = message.getMultimodalContent();
      if (multimodal != null && !multimodal.isEmpty()) {
        totalChars += multimodal.estimateTokenChars();
      } else {
        String content = message.getContent();
        if (content != null) {
          totalChars += content.length();
        }
      }
    }
    BigDecimal charRatio = properties.getMemory().getTokenCharRatio();
    int estimatedPromptTokens = Math.max(1, (int) Math.ceil(totalChars / charRatio.doubleValue()));
    BigDecimal unitPrice = resolveUnitPrice(request.getModel());
    // CostEstimate 为 API 请求值对象，最终序列化为 JSON number；此处将 BigDecimal 单价转为 double 传入（API 边界转换）
    return CostEstimate.estimate(
        estimatedPromptTokens, request.getMaxTokens(), request.getModel(), unitPrice.doubleValue());
  }

  /**
   * 调用后基于实际用量精确核算成本。
   *
   * @param usage 实际 Token 用量
   * @param model 模型名称
   * @return 实际成本核算值对象
   */
  public CostEstimate calculateActual(TokenUsage usage, String model) {
    BigDecimal unitPrice = resolveUnitPrice(model);
    return CostEstimate.actual(usage, model, unitPrice.doubleValue());
  }

  /**
   * 根据模型名称解析单价（USD / 千 Token）。
   *
   * <p>优先从 {@link AgentProperties.Llm#getModelPrices()} 配置中查找；未配置时按模型名前缀匹配默认价格（所有默认价格均已外部化至
   * application.yml 的 {@code agent.llm.model-prices} 配置项）。
   *
   * @param model 模型名称
   * @return 模型单价，未匹配时返回兜底单价
   */
  private BigDecimal resolveUnitPrice(String model) {
    if (model == null || model.isBlank()) {
      return properties.getLlm().getFallbackPrice();
    }
    AgentProperties.Llm llm = properties.getLlm();
    Map<String, Double> priceMap = llm.getModelPrices();
    if (priceMap != null && priceMap.containsKey(model)) {
      return BigDecimal.valueOf(priceMap.get(model));
    }
    return estimatePriceByModelPrefix(model, llm.getFallbackPrice());
  }

  /**
   * 基于模型名前缀估算单价（兜底策略）。
   *
   * <p>当用户未在 {@code ydsz.agent.llm.model-prices} 中配置价格时，按模型名前缀匹配默认单价。 注意：此兜底逻辑仅在 {@code application.yml}
   * 中未覆盖所有已知模型时使用，生产环境应配置完整。
   *
   * @param model 模型名称
   * @return 估算单价
   */
  private BigDecimal estimatePriceByModelPrefix(String model, BigDecimal fallbackPrice) {
    String lower = model.toLowerCase();
    if (lower.contains("gpt-4o-mini")) {
      return new BigDecimal("0.00015");
    }
    if (lower.contains("gpt-4o")) {
      return new BigDecimal("0.0025");
    }
    if (lower.contains("gpt-4-turbo")) {
      return new BigDecimal("0.01");
    }
    if (lower.contains("gpt-3.5")) {
      return new BigDecimal("0.0005");
    }
    if (lower.contains("deepseek")) {
      return new BigDecimal("0.00014");
    }
    return fallbackPrice;
  }
}
