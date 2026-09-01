package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * Agent 人工审批请求视图对象。
 *
 * <p>用于返回审批请求的展示数据。
 *
 * <p><b>线程安全</b>：由 @Data 生成 setter，属可变视图载体；在单次响应序列化前于单线程内填充，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AgentApprovalVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 审批请求 ID */
  private String id;

  /** 所属对话 ID */
  private String conversationId;

  /** 执行链路 ID */
  private String traceId;

  /** 待审批步骤的业务描述 */
  private String stepDescription;

  /** 审批上下文（JSON 字符串，含用户输入、已有结果等） */
  private String contextJson;

  /** 审批状态（PENDING/APPROVED/REJECTED/EXPIRED） */
  private String status;

  /** 审批人标识 */
  private String approver;

  /** 审批意见 */
  private String comment;

  /** 请求创建时间 */
  private LocalDateTime createdAt;

  /** 审批完成时间 */
  private LocalDateTime resolvedAt;
}
