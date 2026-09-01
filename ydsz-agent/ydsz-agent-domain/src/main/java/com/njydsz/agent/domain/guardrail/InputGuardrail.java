package com.njydsz.agent.domain.guardrail;

/**
 * 输入护栏接口
 *
 * <p>在用户消息发送给 LLM 之前进行安全检查，包括：
 *
 * <ul>
 *   <li>敏感词过滤
 *   <li>Prompt 注入检测
 *   <li>PII 脱敏
 *   <li>内容分类（是否允许讨论该话题）
 * </ul>
 *
 * <p><b>线程安全</b>：护栏通常以单例在并发请求中调用，实现应无状态或线程安全；getName/getPriority 须幂等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface InputGuardrail {

  /**
   * 检查用户输入
   *
   * @param input 用户原始输入
   * @return 检查结果
   */
  GuardrailResult check(String input);

  /**
   * 获取护栏名称。
   *
   * @return 护栏名称
   */
  String getName();

  /**
   * 获取优先级。
   *
   * @return 优先级（数字越小优先级越高）
   */
  default int getPriority() {
    return 100;
  }
}
