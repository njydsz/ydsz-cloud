package com.njydsz.agent.domain.execution;

import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.common.exception.custom.BusinessException;

import java.util.List;
import java.util.Objects;

/**
 * 会话暂停异常 — 由工具审批门抛出，通知执行器暂停当前会话并保存检查点。
 *
 * <p>携带待审批的工具调用列表，执行器据此构造 {@link ExecutionCheckpoint}。
 * 本异常用于控制流而非错误处理，调用方应捕获并进入「等待人工审批」状态。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
public class SessionPausedException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /** 审批请求 ID */
  private final String approvalId;

  /** 触发暂停的待审批工具调用 */
  private final List<ToolCall> pendingToolCalls;

  /**
   * 构造会话暂停异常。
   *
   * @param approvalId 审批请求 ID
   * @param pendingToolCalls 待审批工具调用
   */
  public SessionPausedException(String approvalId, List<ToolCall> pendingToolCalls) {
    super("会话已暂停，等待人工审批: " + approvalId);
    this.approvalId = Objects.requireNonNull(approvalId, "approvalId 不能为 null");
    this.pendingToolCalls = pendingToolCalls != null ? List.copyOf(pendingToolCalls) : List.of();
  }

  public String getApprovalId() {
    return approvalId;
  }

  public List<ToolCall> getPendingToolCalls() {
    return pendingToolCalls;
  }
}
