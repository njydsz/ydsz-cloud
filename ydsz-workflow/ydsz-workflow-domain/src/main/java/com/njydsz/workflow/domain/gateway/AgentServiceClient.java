package com.njydsz.workflow.domain.gateway;

import java.util.Map;

/**
 * AI Agent 服务客户端（外部依赖抽象接口）。
 *
 * <p>抽象 ydsz-agent 模块的 Agent 执行能力，domain 层通过本接口调用 AI Agent，
 * infra 层提供适配器实现（Feign 调用 Agent 服务）。
 *
 * <p><b>架构合规说明（1.0.0 DDD 分层规范）：</b>外部依赖抽象接口置于 {@code domain/gateway/} 包下、
 * 以 {@code Client} 结尾（符合 §34.2.1 表格：gateway/ 外部依赖抽象接口）。
 *
 * <p>借鉴 Flowlong 的「AI 审批」概念，将 AI Agent 作为流程节点执行器，
 * 实现自然语言驱动的审批决策自动化。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AgentServiceClient {

  /**
   * 同步执行 AI Agent 并返回审批决策结果。
   *
   * <p>组装请求 → 调用 Agent → 等待响应 → 解析输出 → 返回审批结论。
   *
   * @param agentId Agent ID（由 ydsz-agent 模块创建并管理）
   * @param prompt 提示词（支持变量替换后的最终文本）
   * @param context 流程上下文变量（实例变量、任务变量、节点配置等）
   * @param timeoutMs 超时时间（毫秒）
   * @return Agent 执行输出（包含 approve/reject 决策、reason 原因、confidence 置信度等）
   * @throws com.njydsz.workflow.domain.exception.WorkflowException Agent 不存在、超时或执行异常
   */
  AgentExecutionResult execute(String agentId, String prompt, Map<String, Object> context,
      int timeoutMs);

  /**
   * Agent 执行结果值对象。
   *
   * <p>封装 AI Agent 返回的审批决策信息。
   *
   * @param approve 审批决策：true-通过，false-驳回
   * @param reason 决策原因说明
   * @param confidence Agent 置信度（0.0~1.0）
   * @param rawOutput Agent 原始输出内容
   */
  record AgentExecutionResult(
      boolean approve,
      String reason,
      double confidence,
      String rawOutput) {

    /**
     * 通过结果的快捷构造。
     *
     * @param reason 通过原因
     * @return 通过结果
     */
    public static AgentExecutionResult passed(String reason) {
      return new AgentExecutionResult(true, reason, 1.0, "");
    }

    /**
     * 驳回结果的快捷构造。
     *
     * @param reason 驳回原因
     * @return 驳回结果
     */
    public static AgentExecutionResult rejected(String reason) {
      return new AgentExecutionResult(false, reason, 1.0, "");
    }

    /**
     * 异常结果的快捷构造。
     *
     * @param reason 异常原因
     * @return 异常结果
     */
    public static AgentExecutionResult error(String reason) {
      return new AgentExecutionResult(false, reason, 0.0, "");
    }
  }
}
