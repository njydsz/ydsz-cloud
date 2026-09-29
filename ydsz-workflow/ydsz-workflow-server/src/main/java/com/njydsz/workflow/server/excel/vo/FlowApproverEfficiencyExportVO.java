package com.njydsz.workflow.server.excel.vo;

import java.io.Serial;
import java.io.Serializable;

import com.njydsz.common.excel.annotation.ExcelProperty;

import lombok.Data;

/**
 * 办理人效率 Excel 导出 VO
 *
 * <p>P3-1: 实现 FlowAnalyticsServiceImpl Javadoc 声明的「导出为 Excel」能力。
 * 置于 server/excel/vo 避免反向依赖 + domain 层纯净。
 *
 * @author ydsz-team
 * @since 26.09.30
 */
@Data
public class FlowApproverEfficiencyExportVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 办理人用户 ID */
  @ExcelProperty(value = "办理人ID", order = 1, width = 20)
  private String userId;

  /** 办理人姓名 */
  @ExcelProperty(value = "办理人姓名", order = 2, width = 15)
  private String userName;

  /** 已完成审批数量 */
  @ExcelProperty(value = "已完成数", order = 3, width = 12)
  private long completedCount;

  /** 平均审批耗时（小时） */
  @ExcelProperty(value = "平均耗时(h)", order = 4, width = 12)
  private double avgDurationHours;

  /** 累计审批耗时（小时） */
  @ExcelProperty(value = "累计耗时(h)", order = 5, width = 12)
  private double totalDurationHours;
}
