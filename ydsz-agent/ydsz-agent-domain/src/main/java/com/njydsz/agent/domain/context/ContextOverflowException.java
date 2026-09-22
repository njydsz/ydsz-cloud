package com.njydsz.agent.domain.context;

import com.njydsz.common.exception.custom.BusinessException;

/**
 * 上下文溢出异常 — LLM 请求超出模型上下文窗口时抛出。
 *
 * <p>区别于其他 LlmException，此异常触发时会启动自动压缩兜底流程：
 * 捕获异常 — 调用 {@link ContextCompressor} 压缩消息 — 重试请求。
 *
 * <p>对标 AgentScope 的 context_length_exceeded 自动压缩重试机制。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
public class ContextOverflowException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /** 当前消息列表的估算 Token 数 */
  private final int currentTokens;

  /** 模型允许的最大上下文 Token 数 */
  private final int maxContextTokens;

  public ContextOverflowException(int currentTokens, int maxContextTokens) {
    super(String.format(
        "上下文超限: 当前 %d tokens, 上限 %d tokens",
        currentTokens, maxContextTokens));
    this.currentTokens = currentTokens;
    this.maxContextTokens = maxContextTokens;
  }

  public ContextOverflowException(
      int currentTokens, int maxContextTokens, Throwable cause) {
    super(String.format(
        "上下文超限: 当前 %d tokens, 上限 %d tokens",
        currentTokens, maxContextTokens), cause);
    this.currentTokens = currentTokens;
    this.maxContextTokens = maxContextTokens;
  }

  public int getCurrentTokens() {
    return currentTokens;
  }

  public int getMaxContextTokens() {
    return maxContextTokens;
  }
}
