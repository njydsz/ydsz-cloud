package com.njydsz.system.domain.approval;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 配置变更审批单视图对象（返回前端）。
 *
 * <p>封装审批单的完整展示信息，包括审计日志时间线（JSON 解析后填充）。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Data
public class ConfigApprovalVO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 审批单唯一 ID */
  private String id;

  /** 审批标题 */
  private String title;

  /** 资源类型（CONFIG / DICT / VARIABLE） */
  private String resourceType;

  /** 资源唯一标识 */
  private String resourceKey;

  /** 资源分组 */
  private String resourceGroup;

  /** 变更操作类型（CREATE / UPDATE / DELETE） */
  private String changeType;

  /** 变更前的 JSON 值 */
  private String beforeJson;

  /** 变更后的 JSON 值 */
  private String afterJson;

  /** 审批状态（PENDING / APPROVED / REJECTED / WITHDRAWN） */
  private String status;

  /** 发起人 ID */
  private String submitterId;

  /** 发起人姓名 */
  private String submitterName;

  /** 发起时间 */
  private LocalDateTime submittedAt;

  /** 当前审批人姓名 */
  private String currentApproverName;

  /** 变更原因 */
  private String reason;

  /** 拒绝原因 */
  private String rejectionReason;

  /** 审批单关闭时间 */
  private LocalDateTime closedAt;
}
