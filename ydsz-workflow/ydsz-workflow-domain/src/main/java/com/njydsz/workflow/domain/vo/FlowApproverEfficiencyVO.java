package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 办理人效率统计视图对象
 *
 * <p>用于展示指定办理人的审批效率数据，包括完成数量、平均耗时及累计耗时。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowApproverEfficiencyVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 办理人用户 ID */
  private String userId;

  /** 办理人姓名 */
  private String userName;

  /** 已完成审批数量 */
  private long completedCount;

  /** 平均审批耗时（毫秒） */
  private long avgDurationMs;

  /** 累计审批耗时（毫秒） */
  private long totalDurationMs;
}
