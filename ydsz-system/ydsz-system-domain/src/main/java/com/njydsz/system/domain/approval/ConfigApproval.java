package com.njydsz.system.domain.approval;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 配置变更审批单实体（持久化）。
 *
 * <p>对应数据库表 {@code ydsz_system_config_approval}，记录配置/字典/变量变更的审批流。
 *
 * <p><b>状态流转：</b> PENDING → APPROVED / REJECTED / WITHDRAWN
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Data
@TableName("ydsz_system_config_approval")
public class ConfigApproval implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 审批单唯一 ID */
  @TableId(value = "id", type = IdType.ASSIGN_ID)
  private String id;

  /** 资源类型（CONFIG / DICT / VARIABLE） */
  @TableField("resource_type")
  private String resourceType;

  /** 资源唯一标识 */
  @TableField("resource_key")
  private String resourceKey;

  /** 资源分组（仅 CONFIG 类型有值） */
  @TableField("resource_group")
  private String resourceGroup;

  /** 变更操作类型（CREATE / UPDATE / DELETE） */
  @TableField("change_type")
  private String changeType;

  /** 变更前的 JSON 值（CREATE 时为空） */
  @TableField("before_json")
  private String beforeJson;

  /** 变更后的 JSON 值（DELETE 时为空） */
  @TableField("after_json")
  private String afterJson;

  /** 审批状态（PENDING / APPROVED / REJECTED / WITHDRAWN） */
  @TableField("status")
  private String status;

  /** 发起人 ID */
  @TableField("submitter_id")
  private String submitterId;

  /** 发起时间 */
  @TableField("submitted_at")
  private LocalDateTime submittedAt;

  /** 变更原因 */
  private String reason;

  /** 拒绝原因（REJECTED 时有值） */
  @TableField("rejection_reason")
  private String rejectionReason;

  /** 审批单关闭时间 */
  @TableField("closed_at")
  private LocalDateTime closedAt;

  /** 创建时间 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 更新时间 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
