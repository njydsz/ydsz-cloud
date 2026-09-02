package com.njydsz.literule.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 审批记录 DTO（统一新增/修改）。
 *
 * <p>创建时 {@code recordId} 字段不传，更新时传入 {@code recordId}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class ApprovalRecordDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 审批记录 ID（主键，更新时传入） */
  private String recordId;

  /** 关联规则编码 */
  private String ruleCode;

  /** 关联审批流编码 */
  private String flowCode;

  /** 当前审批层级（从 1 开始） */
  private int currentLevel;

  /** 当前审批状态（PENDING/APPROVED/REJECTED/DELEGATED/CANCELLED） */
  private String currentStatus;

  /** 审批日志 */
  private List<ApprovalLogDTO> logs;

  /** 当前级别已通过审批人列表 */
  private List<String> currentLevelApprovedApprovers;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /**
   * 审批日志 DTO（嵌套）
   */
  @Data
  public static class ApprovalLogDTO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 审批级别 */
    private int level;

    /** 审批人 */
    private String approver;

    /** 动作（APPROVE/REJECT/DELEGATE/COMMENT/SUBMIT/CANCEL） */
    private String action;

    /** 审批意见 */
    private String comment;

    /** 委托给（DELEGATE 时被委托人工号） */
    private String delegatedTo;

    /** 操作时间 */
    private LocalDateTime timestamp;
  }
}
