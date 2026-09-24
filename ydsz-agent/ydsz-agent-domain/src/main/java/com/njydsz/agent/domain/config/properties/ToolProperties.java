package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool 工具集配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.tool}，控制 Agent 工具调用的超时时间、嵌套深度上限、
 * 并行执行策略、失败快速中断、结果裁剪（Eviction）策略。
 * 默认启用（isEnabled=true），超时 30 秒，最大嵌套深度 5，失败时快速中断。
 * 裁剪启用后单条结果最多 2,000 字符、全部结果最多 10,000 字符。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolProperties {
  private static final int DEFAULT_TIMEOUT_MS = 30000;
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int DEFAULT_MAX_DEPTH = 5;
  private static final int DEFAULT_EVICTION_MAX_RESULT_CHARS = 2_000;
  private static final int DEFAULT_EVICTION_MAX_TOTAL_CHARS = 10_000;

  private boolean isEnabled = true;
  private int timeoutMs = DEFAULT_TIMEOUT_MS;
  private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
  private int maxDepth = DEFAULT_MAX_DEPTH;
  private boolean isParallelEnabled = false;
  private boolean isFailFast = true;
  private boolean isEvictionEnabled = false;
  private int evictionMaxResultChars = DEFAULT_EVICTION_MAX_RESULT_CHARS;
  private int evictionMaxTotalChars = DEFAULT_EVICTION_MAX_TOTAL_CHARS;
  private int evictionMaxResultTokens = -1;
}
