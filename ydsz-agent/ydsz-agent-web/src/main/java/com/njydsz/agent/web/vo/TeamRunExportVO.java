package com.njydsz.agent.web.vo;

import java.io.Serial;
import java.io.Serializable;

import com.njydsz.common.excel.annotation.ExcelProperty;

import lombok.Data;

/**
 * Team Run 多 Agent 协作列表 Excel 导出 VO。
 *
 * <p>导出当前租户下活跃的 Team Run 列表，便于运维审计与协作结果归档。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class TeamRunExportVO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** Team Run ID */
  @ExcelProperty(value = "TeamRun ID", order = 1, width = 22)
  private String teamRunId;

  /** 标题 */
  @ExcelProperty(value = "标题", order = 2, width = 25)
  private String title;

  /** 描述 */
  @ExcelProperty(value = "描述", order = 3, width = 30)
  private String description;

  /** 协作模式 */
  @ExcelProperty(value = "协作模式", order = 4, width = 12)
  private String pattern;

  /** 当前状态 */
  @ExcelProperty(value = "当前状态", order = 5, width = 12)
  private String status;

  /** 发起人 */
  @ExcelProperty(value = "发起人", order = 6, width = 14)
  private String initiatedBy;

  /** 成员数量 */
  @ExcelProperty(value = "成员数量", order = 7, width = 10)
  private String memberCount;

  /** 创建时间 */
  @ExcelProperty(value = "创建时间", order = 8, width = 20)
  private String createdAt;

  /** 启动时间 */
  @ExcelProperty(value = "启动时间", order = 9, width = 20)
  private String startedAt;

  /** 完成时间 */
  @ExcelProperty(value = "完成时间", order = 10, width = 20)
  private String completedAt;
}
