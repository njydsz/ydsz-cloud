package com.njydsz.agent.server.middleware;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareContext;

/**
 * 工具结果驱逐中间件 — 在 Acting 阶段按 Token/字符预算压缩工具执行结果。
 *
 * <p>挂在 {@code onActing} 阶段（优先级 {@link AgentMiddleware#TOOL_EVICTION_PRIORITY}，
 * 晚于审计中间件）：工具实际执行完成后，本中间件对结果视图做压缩驱逐，
 * 避免长文本/大 JSON 结果塞爆后续 LLM 上下文窗口。
 *
 * <p><b>不丢证据链</b>：工具原始结果已由 {@code TraceRecorder} 在
 * {@code AbstractAgentExecutor#executeToolsConcurrently} 内持久化，
 * 本中间件只修改返回给 LLM 的 {@link MiddlewareContext#getToolResults()} 视图，
 * 链路追踪与审计仍能看到完整原始结果。
 *
 * <p><b>默认关闭</b>：{@code ydsz.agent.tool.eviction-enabled=false} 时中间件直接放行，
 * 不修改任何结果（零行为变更）。开启后按以下顺序处理：
 *
 * <ol>
 *   <li>单个结果字符上限 {@code evictionMaxResultChars}</li>
 *   <li>单个结果 Token 上限 {@code evictionMaxResultTokens}（按字符/2.5 估算）</li>
 *   <li>本批次总字符上限 {@code evictionMaxTotalChars}（按比例压缩）</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Slf4j
@Component
public class ToolResultEvictionMiddleware implements AgentMiddleware {

  /** 中间件名称 */
  private static final String NAME = "tool-result-eviction";

  /** 默认字符/Token 估算比例 */
  private static final double DEFAULT_TOKEN_CHAR_RATIO = 2.5;

  /** 截断标记 */
  private static final String TRUNCATION_MARKER = "...[truncated]";

  /** Agent 配置 */
  private final AgentProperties properties;

  /**
   * 构造工具结果驱逐中间件。
   *
   * @param properties Agent 配置属性
   */
  public ToolResultEvictionMiddleware(AgentProperties properties) {
    this.properties = properties;
    if (isEnabled()) {
      log.info(
          "[ToolResultEviction] 工具结果驱逐已启用: maxResultChars={}, maxResultTokens={}, maxTotalChars={}",
          getMaxResultChars(),
          getMaxResultTokens(),
          getMaxTotalChars());
    }
  }

  @Override
  public void onActing(MiddlewareContext context, ActingProceed proceed) {
    Map<String, String> results = proceed.execute();
    if (!isEnabled() || results == null || results.isEmpty()) {
      context.setToolResults(results);
      return;
    }
    Map<String, String> compressed = applyPerResultEviction(results);
    compressed = applyTotalEviction(compressed);
    context.setToolResults(compressed);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int getPriority() {
    return TOOL_EVICTION_PRIORITY;
  }

  /**
   * 判断是否启用驱逐。
   *
   * @return true 表示启用
   */
  private boolean isEnabled() {
    return properties != null
        && properties.getTool() != null
        && properties.getTool().isEvictionEnabled();
  }

  /**
   * 获取单结果字符上限。
   *
   * @return 上限值（-1 表示不限制）
   */
  private int getMaxResultChars() {
    if (properties == null || properties.getTool() == null) {
      return -1;
    }
    return properties.getTool().getEvictionMaxResultChars();
  }

  /**
   * 获取单结果 Token 上限。
   *
   * @return 上限值（-1 表示不限制）
   */
  private int getMaxResultTokens() {
    if (properties == null || properties.getTool() == null) {
      return -1;
    }
    return properties.getTool().getEvictionMaxResultTokens();
  }

  /**
   * 获取本批次总字符上限。
   *
   * @return 上限值（-1 表示不限制）
   */
  private int getMaxTotalChars() {
    if (properties == null || properties.getTool() == null) {
      return -1;
    }
    return properties.getTool().getEvictionMaxTotalChars();
  }

  /**
   * 应用单结果字符 / Token 上限。
   *
   * @param results 原始结果表
   * @return 压缩后的结果表
   */
  private Map<String, String> applyPerResultEviction(Map<String, String> results) {
    int maxChars = getMaxResultChars();
    int maxTokens = getMaxResultTokens();
    if (maxChars <= 0 && maxTokens <= 0) {
      return results;
    }
    Map<String, String> compressed = new HashMap<>(results.size() * 2);
    for (Map.Entry<String, String> entry : results.entrySet()) {
      String value = entry.getValue();
      if (value != null) {
        value = applyPerCharLimit(value, maxChars);
        value = applyPerTokenLimit(value, maxTokens);
      }
      compressed.put(entry.getKey(), value);
    }
    return compressed;
  }

  /**
   * 按字符上限截断单结果。
   *
   * @param value 原始结果
   * @param maxChars 最大字符数（<=0 表示不限制）
   * @return 截断后的结果
   */
  private String applyPerCharLimit(String value, int maxChars) {
    if (maxChars <= 0 || value.length() <= maxChars) {
      return value;
    }
    return truncate(value, maxChars);
  }

  /**
   * 按 Token 上限截断单结果。
   *
   * @param value 原始结果
   * @param maxTokens 最大 Token 数（<=0 表示不限制）
   * @return 截断后的结果
   */
  private String applyPerTokenLimit(String value, int maxTokens) {
    if (maxTokens <= 0) {
      return value;
    }
    int maxChars = (int) Math.floor(maxTokens * DEFAULT_TOKEN_CHAR_RATIO);
    if (value.length() <= maxChars) {
      return value;
    }
    return truncate(value, maxChars);
  }

  /**
   * 按比例压缩本批次总结果，使总字符不超过上限。
   *
   * <p>在单结果上限已应用的基础上，若总字符仍超限，则按整体比例压缩各结果。
   * 为严格保证总字符不超限，每个结果额外受 {@code maxTotalChars / n} 的硬顶约束。
   *
   * @param results 单结果上限已处理的结果表
   * @return 总字符不超限的结果表
   */
  private Map<String, String> applyTotalEviction(Map<String, String> results) {
    int maxTotalChars = getMaxTotalChars();
    if (maxTotalChars <= 0) {
      return results;
    }
    long totalChars = results.values().stream().mapToLong(v -> v != null ? v.length() : 0).sum();
    if (totalChars <= maxTotalChars) {
      return results;
    }
    int resultCount = results.size();
    int perResultCap = Math.max(1, maxTotalChars / resultCount);
    double ratio = (double) maxTotalChars / totalChars;
    Map<String, String> compressed = new HashMap<>(results.size() * 2);
    for (Map.Entry<String, String> entry : results.entrySet()) {
      String value = entry.getValue();
      if (value != null) {
        int proportionalTarget = (int) (value.length() * ratio);
        int targetLength = Math.min(perResultCap, proportionalTarget);
        if (value.length() > targetLength) {
          value = truncate(value, targetLength);
        }
      }
      compressed.put(entry.getKey(), value);
    }
    return compressed;
  }

  /**
   * 截断字符串并附加截断标记。
   *
   * <p>结果长度不会超过 {@code maxLength}；当 {@code maxLength} 小于标记长度时，
   * 仅返回标记本身。
   *
   * @param value 原始字符串
   * @param maxLength 最大保留字符数
   * @return 截断后的字符串
   */
  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    int markerLength = TRUNCATION_MARKER.length();
    int keep = maxLength - markerLength;
    if (keep <= 0) {
      return TRUNCATION_MARKER;
    }
    return value.substring(0, keep) + TRUNCATION_MARKER;
  }
}
