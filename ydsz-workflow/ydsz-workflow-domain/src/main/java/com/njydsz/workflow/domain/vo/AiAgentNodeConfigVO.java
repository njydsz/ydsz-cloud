package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;

import com.njydsz.common.json.YdszJson;

/**
 * AI 审批节点配置值对象。
 *
 * <p>封装节点 ext JSON 中 AI_AGENT 类型节点的执行配置，提供类型安全的访问方式。
 *
 * <p><b>ext JSON 配置：</b>
 *
 * <ul>
 *   <li>{@code agentId}：Agent ID（必填），由 ydsz-agent 模块创建并管理
 *   <li>{@code promptTemplate}：提示词模板（支持 {@code ${variable}} 占位符替换）
 *   <li>{@code outputSchema}：期望输出 JSON Schema（校验 Agent 输出结构）
 *   <li>{@code fallbackStrategy}：Agent 超时/异常时的兜底策略（AUTO_PASS / AUTO_REJECT /
 *       TRANSFER_ADMIN / RETRY，默认 AUTO_PASS）
 *   <li>{@code retryMax}：最大重试次数（默认 1）
 *   <li>{@code timeoutMs}：单次调用超时毫秒（默认 30000）
 * </ul>
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>值对象置于 {@code domain/vo/} 包下，
 * 以 {@code Config} 结尾，不可变对象（所有字段 final）。
 *
 * <p>实现了 Flowlong 的「AI 审批」概念，与 ydsz-agent 模块联动，支持自然语言审批决策。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.workflow.domain.enums.FlowNodeType#AI_AGENT
 */
@Getter
@ToString
public class AiAgentNodeConfigVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 默认超时时间（毫秒） */
  public static final int DEFAULT_TIMEOUT_MS = 30000;

  /** 默认最大重试次数 */
  public static final int DEFAULT_RETRY_MAX = 1;

  /** 默认兜底策略 */
  public static final FallbackStrategy DEFAULT_FALLBACK_STRATEGY = FallbackStrategy.AUTO_PASS;

  /** Agent ID（由 ydsz-agent 模块创建并管理） */
  private final String agentId;

  /** 提示词模板（支持 ${variable} 占位符替换） */
  private final String promptTemplate;

  /** 期望输出 JSON Schema */
  private final String outputSchema;

  /** 兜底策略 */
  private final FallbackStrategy fallbackStrategy;

  /** 最大重试次数 */
  private final int retryMax;

  /** 单次调用超时毫秒 */
  private final int timeoutMs;

  private AiAgentNodeConfigVO(
      String agentId,
      String promptTemplate,
      String outputSchema,
      FallbackStrategy fallbackStrategy,
      int retryMax,
      int timeoutMs) {
    this.agentId = agentId != null ? agentId : "";
    this.promptTemplate = promptTemplate != null ? promptTemplate : "";
    this.outputSchema = outputSchema != null ? outputSchema : "";
    this.fallbackStrategy = fallbackStrategy != null ? fallbackStrategy : DEFAULT_FALLBACK_STRATEGY;
    this.retryMax = retryMax > 0 ? retryMax : DEFAULT_RETRY_MAX;
    this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
  }

  /**
   * 从 ext JSON Map 解析 AI 审批节点配置。
   *
   * @param extMap 节点 ext JSON 解析后的 Map，不可为 null
   * @return AI 审批节点配置值对象（不可变）
   */
  public static AiAgentNodeConfigVO fromExt(Map<String, Object> extMap) {
    if (extMap == null || extMap.isEmpty()) {
      return new AiAgentNodeConfigVO("", "", "", DEFAULT_FALLBACK_STRATEGY, DEFAULT_RETRY_MAX,
          DEFAULT_TIMEOUT_MS);
    }
    String agentId = parseStringSafe(extMap.get("agentId"));
    String promptTemplate = parseStringSafe(extMap.get("promptTemplate"));
    String outputSchema = parseStringSafe(extMap.get("outputSchema"));
    FallbackStrategy strategy = parseFallbackStrategy(extMap.get("fallbackStrategy"));
    int retryMax = parsePositiveInt(extMap.get("retryMax"), DEFAULT_RETRY_MAX);
    int timeoutMs = parsePositiveInt(extMap.get("timeoutMs"), DEFAULT_TIMEOUT_MS);
    return new AiAgentNodeConfigVO(agentId, promptTemplate, outputSchema, strategy, retryMax,
        timeoutMs);
  }

  /**
   * 从 ext JSON 字符串解析 AI 审批节点配置。
   *
   * @param extJson ext JSON 字符串，可为 null 或空
   * @return AI 审批节点配置值对象（不可变）
   */
  public static AiAgentNodeConfigVO fromExtJson(String extJson) {
    if (extJson == null || extJson.isBlank()) {
      return new AiAgentNodeConfigVO("", "", "", DEFAULT_FALLBACK_STRATEGY, DEFAULT_RETRY_MAX,
          DEFAULT_TIMEOUT_MS);
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(extJson);
      return fromExt(map);
    } catch (Exception e) {
      return new AiAgentNodeConfigVO("", "", "", DEFAULT_FALLBACK_STRATEGY, DEFAULT_RETRY_MAX,
          DEFAULT_TIMEOUT_MS);
    }
  }

  /**
   * 是否配置了有效的 Agent ID。
   *
   * @return true 表示 agentId 非空
   */
  public boolean hasValidAgentId() {
    return agentId != null && !agentId.isBlank();
  }

  /**
   * 是否为 RETRY 兜底策略。
   *
   * @return true 表示超时/异常时会重试
   */
  public boolean isRetryFallback() {
    return fallbackStrategy == FallbackStrategy.RETRY;
  }

  /**
   * AI 审批节点兜底策略枚举。
   *
   * <p>当 AI Agent 调用超时或异常时的处理方式。
   */
  public enum FallbackStrategy {
    /** 自动通过 */
    AUTO_PASS,
    /** 自动驳回 */
    AUTO_REJECT,
    /** 转交给管理员 */
    TRANSFER_ADMIN,
    /** 重试 */
    RETRY
  }

  // ==================== 内部工具方法 ====================

  private static String parseStringSafe(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static FallbackStrategy parseFallbackStrategy(Object value) {
    if (value == null) {
      return DEFAULT_FALLBACK_STRATEGY;
    }
    String name = String.valueOf(value).toUpperCase();
    try {
      return FallbackStrategy.valueOf(name);
    } catch (IllegalArgumentException e) {
      return DEFAULT_FALLBACK_STRATEGY;
    }
  }

  private static int parsePositiveInt(Object value, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    if (value instanceof Number n) {
      int v = n.intValue();
      return v > 0 ? v : defaultValue;
    }
    try {
      int v = Integer.parseInt(String.valueOf(value).trim());
      return v > 0 ? v : defaultValue;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
