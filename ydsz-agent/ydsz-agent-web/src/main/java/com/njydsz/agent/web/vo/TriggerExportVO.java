package com.njydsz.agent.web.vo;

import java.io.Serial;
import java.io.Serializable;

import com.njydsz.common.excel.annotation.ExcelProperty;

import lombok.Data;

/**
 * 触发器 Excel 导出 VO。
 *
 * <p>导出租户下的启用触发器列表，用于合规审计与问题排查。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class TriggerExportVO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 触发器 ID */
  @ExcelProperty(value = "触发器ID", order = 1, width = 22)
  private String triggerId;

  /** 触发器名称 */
  @ExcelProperty(value = "触发器名称", order = 2, width = 18)
  private String name;

  /** 触发类型 */
  @ExcelProperty(value = "触发类型", order = 3, width = 14)
  private String triggerType;

  /** 目标 Agent */
  @ExcelProperty(value = "目标Agent", order = 4, width = 22)
  private String targetAgentCode;

  /** 目标 Agent 类型 */
  @ExcelProperty(value = "目标Agent类型", order = 5, width = 16)
  private String targetAgentType;

  /** Cron 表达式 */
  @ExcelProperty(value = "Cron表达式", order = 6, width = 18)
  private String cronExpression;

  /** 消息匹配模式 (正则) */
  @ExcelProperty(value = "匹配模式", order = 7, width = 18)
  private String matchPattern;

  /** 启用状态 */
  @ExcelProperty(value = "启用状态", order = 8, width = 10)
  private String isEnabled;

  /** 每小时最大执行次数 */
  @ExcelProperty(value = "时最大执行次数", order = 9, width = 14)
  private String maxExecutionsPerHour;

  /** 上次触发时间 */
  @ExcelProperty(value = "上次触发时间", order = 10, width = 20)
  private String lastTriggeredAt;

  /** 累计触发次数 */
  @ExcelProperty(value = "累计触发次数", order = 11, width = 12)
  private String totalTriggerCount;

  /** 创建时间 */
  @ExcelProperty(value = "创建时间", order = 12, width = 20)
  private String createdAt;
}
