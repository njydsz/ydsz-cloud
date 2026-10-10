package com.njydsz.agent.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 单次 LLM 调用的成本估算与核算值对象。
 *
 * <p>在调用 LLM 之前基于字符数进行 Token 预估，用于前端成本展示和配额预检； 在实际调用完成后基于 {@link TokenUsage} 精确核算，用于用量记录与计费。
 *
 * <p>所有金额单位为 USD，精度到小数点后 6 位（微美元级）。
 *
 * <p><b>线程安全</b>：全字段 final 不可变值对象，可安全跨线程共享。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public final class CostEstimate {

  /** 金额保留小数位 */
  private static final int PRICE_SCALE = 6;


  /** 估算的 Prompt Token 数（调用前预检） */
  private final int estimatedPromptTokens;

  /** 估算的 Completion Token 数（调用前预检，基于 maxTokens 配置） */
  private final int estimatedCompletionTokens;

  /** 估算的总 Token 数（调用前预检） */
  private final int estimatedTotalTokens;

  /** 估算成本（USD，调用前预检） */
  private final BigDecimal estimatedCostUsd;

  /** 实际 Prompt Token 数（调用后核算，可能为 0 表示尚未核算） */
  private final int actualPromptTokens;

  /** 实际 Completion Token 数（调用后核算，可能为 0 表示尚未核算） */
  private final int actualCompletionTokens;

  /** 实际总 Token 数（调用后核算，可能为 0 表示尚未核算） */
  private final int actualTotalTokens;

  /** 实际成本（USD，调用后核算） */
  private final BigDecimal actualCostUsd;

  /** 本次调用使用的模型名称 */
  private final String model;

  /** 模型单价（USD / 千 Token） */
  private final BigDecimal unitPrice;

  public CostEstimate(
      int estimatedPromptTokens,
      int estimatedCompletionTokens,
      int estimatedTotalTokens,
      BigDecimal estimatedCostUsd,
      int actualPromptTokens,
      int actualCompletionTokens,
      int actualTotalTokens,
      BigDecimal actualCostUsd,
      String model,
      BigDecimal unitPrice) {
    this.estimatedPromptTokens = estimatedPromptTokens;
    this.estimatedCompletionTokens = estimatedCompletionTokens;
    this.estimatedTotalTokens = estimatedTotalTokens;
    this.estimatedCostUsd = (estimatedCostUsd == null ? BigDecimal.ZERO : estimatedCostUsd).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    this.actualPromptTokens = actualPromptTokens;
    this.actualCompletionTokens = actualCompletionTokens;
    this.actualTotalTokens = actualTotalTokens;
    this.actualCostUsd = (actualCostUsd == null ? BigDecimal.ZERO : actualCostUsd).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    this.model = model;
    this.unitPrice = unitPrice;
  }

  /**
   * 创建调用前的成本估算。
   *
   * @param estimatedPromptTokens 估算 Prompt Token 数
   * @param maxTokens 最大生成 Token 数配置
   * @param model 模型名称
   * @param unitPrice 模型单价（USD / 千 Token）
   * @return 仅含估算值的 CostEstimate 实例
   */
  public static CostEstimate estimate(
      int estimatedPromptTokens, int maxTokens, String model, BigDecimal unitPrice) {
    int estimatedCompletion = maxTokens;
    int estimatedTotal = estimatedPromptTokens + estimatedCompletion;
    BigDecimal estimatedCost = BigDecimal.valueOf(estimatedTotal).multiply(unitPrice).divide(BigDecimal.valueOf(1000), PRICE_SCALE, RoundingMode.HALF_UP);
    return new CostEstimate(
        estimatedPromptTokens,
        estimatedCompletion,
        estimatedTotal,
        estimatedCost,
        0,
        0,
        0,
        BigDecimal.ZERO,
        model,
        unitPrice);
  }

  /**
   * 基于实际用量精确核算成本。
   *
   * @param usage 实际 Token 用量
   * @param model 模型名称
   * @param unitPrice 模型单价（USD / 千 Token）
   * @return 含精确核算值的 CostEstimate 实例
   */
  public static CostEstimate actual(TokenUsage usage, String model, BigDecimal unitPrice) {
    if (usage == null) {
      return new CostEstimate(0, 0, 0, BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, model, unitPrice);
    }
    int prompt = usage.getPromptTokens();
    int completion = usage.getCompletionTokens();
    int total = usage.getTotalTokens();
    BigDecimal cost = BigDecimal.valueOf(total).multiply(unitPrice).divide(BigDecimal.valueOf(1000), PRICE_SCALE, RoundingMode.HALF_UP);
    return new CostEstimate(0, 0, 0, BigDecimal.ZERO, prompt, completion, total, cost, model, unitPrice);
  }

  public int getEstimatedPromptTokens() {
    return estimatedPromptTokens;
  }

  public int getEstimatedCompletionTokens() {
    return estimatedCompletionTokens;
  }

  public int getEstimatedTotalTokens() {
    return estimatedTotalTokens;
  }

  public BigDecimal getEstimatedCostUsd() {
    return estimatedCostUsd;
  }

  public int getActualPromptTokens() {
    return actualPromptTokens;
  }

  public int getActualCompletionTokens() {
    return actualCompletionTokens;
  }

  public int getActualTotalTokens() {
    return actualTotalTokens;
  }

  public BigDecimal getActualCostUsd() {
    return actualCostUsd;
  }

  public String getModel() {
    return model;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  @Override
  public String toString() {
    return "CostEstimate{model='"
        + model
        + "', estimatedTotalTokens="
        + estimatedTotalTokens
        + ", estimatedCostUsd="
        + estimatedCostUsd
        + ", actualTotalTokens="
        + actualTotalTokens
        + ", actualCostUsd="
        + actualCostUsd
        + '}';
  }
}
