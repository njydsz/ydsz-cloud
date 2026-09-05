package com.njydsz.literule.server.approval;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批记录（P1-3 多级审批流）
 *
 * <p>记录一条规则的完整审批流转状态，包括当前级别、当前状态、审批日志等。 审批记录在 {@link RuleApprovalService} 中以内存 Map 存储，消费方可通过 持久化
 * SPI（ApprovalRecordRepository）落库。
 *
 * <p>当前状态（{@link #currentStatus}）取值：
 *
 * <ul>
 *   <li>{@link #STATUS_PENDING} - 审批中
 *   <li>{@link #STATUS_APPROVED} - 全部通过（已发布）
 *   <li>{@link #STATUS_REJECTED} - 已拒绝（已归档）
 *   <li>{@link #STATUS_DELEGATED} - 已委托（等待被委托人审批）
 *   <li>{@link #STATUS_CANCELLED} - 已撤回
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRecord implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 状态常量：审批中 */
  public static final String STATUS_PENDING = "PENDING";

  /** 状态常量：全部通过 */
  public static final String STATUS_APPROVED = "APPROVED";

  /** 状态常量：已拒绝 */
  public static final String STATUS_REJECTED = "REJECTED";

  /** 状态常量：已委托 */
  public static final String STATUS_DELEGATED = "DELEGATED";

  /** 状态常量：已撤回 */
  public static final String STATUS_CANCELLED = "CANCELLED";

  /** 记录 ID */
  private String recordId;

  /** 规则编码 */
  private String ruleCode;

  /** 流程编码 */
  private String flowCode;

  /** 当前级别 */
  private int currentLevel;

  /** 当前状态（PENDING/APPROVED/REJECTED/DELEGATED/CANCELLED） */
  private String currentStatus;

  /** 申请委托人（P1-3 审批委托） */
  private String delegateFrom;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 最后更新时间 */
  private LocalDateTime updatedAt;

  /** 审批日志列表 */
  @Builder.Default
  private List<ApprovalLog> logs = new ArrayList<>(16);

  /** 当前级别已审批通过的审批人列表（COUNTERSIGN/SEQUENCE 场景使用） */
  @Builder.Default
  private List<String> currentLevelApprovedApprovers = new ArrayList<>(4);

  /**
   * 追加审批日志
   *
   * @param log 审批日志
   */
  public void appendLog(ApprovalLog log) {
    if (logs == null) {
      logs = new ArrayList<>(16);
    }
    logs.add(log);
  }

  /**
   * 获取当前级别已审批通过的审批人列表
   *
   * @return 已审批通过的审批人列表
   */
  public List<String> getCurrentLevelApprovedApprovers() {
    List<String> approvers = new ArrayList<>(4);
    if (logs != null) {
      for (ApprovalLog log : logs) {
        if (log.getLevel() == currentLevel && ApprovalLog.ACTION_APPROVE.equals(log.getAction())) {
          approvers.add(log.getApprover());
        }
      }
    }
    return approvers;
  }

  /**
   * 获取指定级别已审批通过的审批人列表
   *
   * @param level 审批级别
   * @return 已审批通过的审批人列表
   */
  public List<String> currentLevelApprovedApprovers(int level) {
    List<String> approvers = new ArrayList<>(4);
    if (logs != null) {
      for (ApprovalLog log : logs) {
        if (log.getLevel() == level && ApprovalLog.ACTION_APPROVE.equals(log.getAction())) {
          approvers.add(log.getApprover());
        }
      }
    }
    return approvers;
  }

  /**
   * 获取指定级别已审批通过的审批人列表（兼容旧接口）
   *
   * @param levelObj 级别对象（自动拆箱为 int）
   * @return 已审批通过的审批人列表
   */
  public List<String> currentLevelApprovedApprovers(ArrayList<?> levelObj) {
    return getCurrentLevelApprovedApprovers();
  }

  /**
   * 判断审批记录是否处于指定状态
   *
   * @param status 状态
   * @return true 表示处于该状态
   */
  public boolean isStatus(String status) {
    return status != null && status.equals(currentStatus);
  }
}
