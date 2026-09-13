package com.njydsz.agent.domain.context;

import java.util.List;

import com.njydsz.agent.domain.model.ChatMessage;

/**
 * 上下文压缩策略接口 — 对标 AgentScope 的上下文长度超限自动兜底。
 *
 * <p>当 LLM 请求超出模型上下文窗口时，通过压缩策略缩减消息列表后重试，
 * 避免直接截断导致的语义丢失。
 *
 * <p>内置策略（infra 层实现）：
 * <ul>
 *   <li><b>摘要压缩</b>：将老旧消息替换为 LLM 生成的摘要</li>
 *   <li><b>滑动窗口裁剪</b>：丢弃最旧的消息，保留最近 N 轮</li>
 * </ul>
 *
 * <p><b>线程安全</b>：策略实现须无状态，压缩后的消息列表写入返回值而非修改入参。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
public interface ContextCompressor {

  /**
   * 压缩消息列表，返回压缩后的新列表。
   *
   * <p>实现应保证：返回列表大小 <= maxSize，且保留 System Prompt 消息（role=SYSTEM）不被丢弃。
   *
   * @param messages 原始消息列表（不可变入参，返回新列表）
   * @param maxSize 目标最大消息条数或 Token 预算
   * @return 压缩后的消息列表（总 Token 不超过预算）
   */
  List<ChatMessage> compress(List<ChatMessage> messages, int maxSize);

  /**
   * 获取压缩策略名称。
   *
   * @return 策略标识（如 "summary"、"sliding-window"）
   */
  String getName();
}
