package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审批记录视图对象（VO）。
 *
 * <p>用于前端展示某条规则在某审批流中的审批进度与状态。 每行对应一个规则的一次审批实例，记录当前所处层级与状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ApprovalRecordVO {

  /** 审批记录 ID（主键） */
  private String recordId;

  /** 关联规则编码 */
  private String ruleCode;

  /** 关联审批流编码 */
  private String flowCode;

  /** 当前审批层级（从 1 开始，表示在第几级审批） */
  private int currentLevel;

  /** 当前审批状态（如 PENDING/APPROVED/REJECTED） */
  private String currentStatus;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
