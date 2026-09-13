package com.njydsz.agent.server.harness;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.context.ContextCompressor;
import com.njydsz.agent.domain.context.ContextOverflowException;
import com.njydsz.agent.domain.model.ChatMessage;

/**
 * 上下文溢出处理器 — LLM 调用前检测上下文长度，超限时启动压缩兜底。
 *
 * <p>执行流程：
 * <ol>
 *   <li>检测当前消息列表 Token 数</li>
 *   <li>若超限且配置了压缩策略 → 执行压缩 → 通知调用方更新消息列表</li>
 *   <li>若超限且未配置压缩策略 → 抛出 {@link ContextOverflowException}</li>
 * </ol>
 *
 * <p><b>对标 AgentScope</b>：对应 AgentScope 的 context_length_exceeded 自动压缩重试逻辑，
 * 在 LLM 调用前主动检测、主动压缩，而非依赖事后异常捕获。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
public class ContextOverflowHandler {

  /** 上下文压缩策略 */
  private final ContextCompressor compressor;

  /** Token 估算字符系数（char/token） */
  private final double tokenCharRatio;

  /** 默认 Token 估算字符系数 */
  private static final double DEFAULT_TOKEN_CHAR_RATIO = 2.5;

  /**
   * 构造上下文溢出处理器。
   *
   * @param compressor 上下文压缩策略（不为 null 时启用压缩兜底）
   */
  public ContextOverflowHandler(ContextCompressor compressor) {
    this(compressor, DEFAULT_TOKEN_CHAR_RATIO);
  }

  /**
   * 构造上下文溢出处理器（含 Token 估算参数）。
   *
   * @param compressor 上下文压缩策略
   * @param tokenCharRatio Token 估算字符系数
   */
  public ContextOverflowHandler(ContextCompressor compressor, double tokenCharRatio) {
    this.compressor = compressor;
    this.tokenCharRatio = tokenCharRatio;
  }

  /**
   * 检测并压缩消息列表（如需要）。
   *
   * <p>当消息估算 Token 超限时调用 {@link ContextCompressor} 压缩，返回压缩后的新列表。
   * 若未超限或未配置压缩机，返回原始列表。
   *
   * @param messages 当前消息列表
   * @param tokenBudget Token 预算
   * @return 压缩后（或原始）的消息列表
   */
  public List<ChatMessage> compressIfNeeded(List<ChatMessage> messages, int tokenBudget) {
    int estimatedTokens = estimateTotalTokens(messages);
    if (estimatedTokens <= tokenBudget) {
      return messages;
    }
    if (compressor == null) {
      throw new ContextOverflowException(estimatedTokens, tokenBudget);
    }
    log.info("[ContextOverflow] 触发压缩: 当前 {} tokens, 预算 {} tokens, 策略={}",
        estimatedTokens, tokenBudget, compressor.getName());
    return compressor.compress(messages, tokenBudget);
  }

  /**
   * 估算消息列表的总 Token 数。
   *
   * @param messages 消息列表
   * @return 估算 Token 总数
   */
  public int estimateTotalTokens(List<ChatMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return 0;
    }
    int totalChars = 0;
    for (ChatMessage msg : messages) {
      if (msg.getContent() != null) {
        totalChars += msg.getContent().length();
      }
    }
    return Math.max(1, (int) Math.ceil(totalChars / tokenCharRatio));
  }
}
