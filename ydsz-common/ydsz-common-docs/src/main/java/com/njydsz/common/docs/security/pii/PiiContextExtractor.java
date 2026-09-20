package com.njydsz.common.docs.security.pii;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * PII 上下文提取工具
 *
 * <p>从全文中截取命中位置的前后片段，供审计报告与安全审查使用。 上下文长度固定、不含原文本身，避免上下文中夹带完整敏感信息。
 *
 * <p>所有 PII 检测器共享本工具以避免重复实现；上下文窗口大小未来可配置化。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PiiContextExtractor {

  /** 默认上下文窗口大小（字符数） */
  public static final int DEFAULT_WINDOW_SIZE = 30;

  /**
   * 从文本中抽取指定位置的前后上下文。
   *
   * <p>返回的片段长度可能小于 {@code windowSize}：当命中位置贴近文本头尾时自动截断。 入参位置越界或文本为 {@code null} 时返回空串上下文，不抛异常。
   *
   * @param text 全文，为 {@code null} 时返回空上下文
   * @param startIndex 命中起始位置（含）
   * @param endIndex 命中结束位置（不含）
   * @param windowSize 前后各取多少字符（正数）
   * @return 包含 before / after 的记录；永不为 {@code null}
   */
  public static Context extract(String text, int startIndex, int endIndex, int windowSize) {
    if (text == null || text.isEmpty()) {
      return new Context("", "");
    }
    int len = text.length();
    int clampedStart = Math.max(0, startIndex);
    int clampedEnd = Math.min(len, endIndex);

    int beforeFrom = Math.max(0, clampedStart - windowSize);
    String before = text.substring(beforeFrom, clampedStart);

    int afterTo = Math.min(len, clampedEnd + windowSize);
    String after = text.substring(clampedEnd, afterTo);

    return new Context(before, after);
  }

  /**
   * 使用默认窗口大小 {@link #DEFAULT_WINDOW_SIZE} 的快捷方式。
   *
   * @param text 全文
   * @param startIndex 命中起始位置（含）
   * @param endIndex 命中结束位置（不含）
   * @return 包含 before / after 的记录；永不为 {@code null}
   */
  public static Context extract(String text, int startIndex, int endIndex) {
    return extract(text, startIndex, endIndex, DEFAULT_WINDOW_SIZE);
  }

  /** 上下文片段记录 */
  public record Context(String before, String after) {}
}
