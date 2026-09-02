package com.njydsz.agent.server.rag;

/**
 * Token 估算工具
 *
 * <p>基于字符数估算 Token 数量，用于 RAG 上下文截断和记忆压缩的 Token 预算控制。
 *
 * <h3>估算策略</h3>
 *
 * <ul>
 *   <li>中文为主场景（tokenCharRatio=1.5）：约 1.5 个汉字 = 1 Token
 *   <li>中英混合场景（tokenCharRatio=2.5）：默认值，平衡中英文占比
 *   <li>英文为主场景（tokenCharRatio=4.0）：约 4 个英文字符 = 1 Token
 * </ul>
 *
 * <p><b>注意</b>：粗估算法（字符除系数）与真实 Tokenizer 有 ±20% 偏差，但足以满足预算控制需求。 如需精确控制，可替换为 tiktoken / HuggingFace Tokenizer 实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class TokenEstimator {

  /** 默认中英混合字符系数（Char/Token） */
  public static final double DEFAULT_TOKEN_CHAR_RATIO = 2.5;

  /** 工具类不可实例化 */
  private TokenEstimator() {}

  /**
   * 基于字符数估算 Token 数。
   *
   * @param text 文本内容
   * @param tokenCharRatio 字符系数（Char/Token），中文 1.5、英文 4.0、中英混合 2.5
   * @return 估算 Token 数（至少为 0）
   */
  public static int estimate(String text, double tokenCharRatio) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    return Math.max(1, (int) Math.ceil(text.length() / tokenCharRatio));
  }

  /**
   * 基于字符数估算 Token 数（默认中英混合系数 2.5）。
   *
   * @param text 文本内容
   * @return 估算 Token 数
   */
  public static int estimate(String text) {
    return estimate(text, DEFAULT_TOKEN_CHAR_RATIO);
  }

  /**
   * 计算需要截断的字符数，使文本 Token 数不超过预算。
   *
   * @param text 文本内容
   * @param tokenBudget Token 预算
   * @param tokenCharRatio 字符系数
   * @return 可保留的最大字符数
   */
  public static int maxCharsForBudget(String text, int tokenBudget, double tokenCharRatio) {
    return (int) (tokenBudget * tokenCharRatio);
  }
}
