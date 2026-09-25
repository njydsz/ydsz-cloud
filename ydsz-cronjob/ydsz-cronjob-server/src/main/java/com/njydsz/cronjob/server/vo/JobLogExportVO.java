package com.njydsz.cronjob.server.vo;

import java.io.Serial;
import java.io.Serializable;

import com.njydsz.common.excel.annotation.ExcelProperty;

import lombok.Data;

/**
 * 任务执行日志 Excel 导出 VO
 *
 * <p>用于 ydsz-common-excel 映射执行日志到 Excel 列，支撑执行记录回溯与问题排查。
 *
 * <p>流式导出方案：通过 {@link com.njydsz.common.excel.core.ExcelWriter} 分页写入，
 * 避免一次性加载全量数据到内存。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class JobLogExportVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 日志 ID */
  @ExcelProperty(value = "日志ID", order = 1, width = 22)
  private String id;

  /** 任务 KEY */
  @ExcelProperty(value = "任务KEY", order = 2, width = 25)
  private String jobKey;

  /** 开始时间 */
  @ExcelProperty(value = "开始时间", order = 3, width = 20)
  private String startTime;

  /** 结束时间 */
  @ExcelProperty(value = "结束时间", order = 4, width = 20)
  private String endTime;

  /** 耗时（毫秒） */
  @ExcelProperty(value = "耗时(ms)", order = 5, width = 12)
  private Long durationMs;

  /** 状态（SUCCESS/FAILED/TIMEOUT） */
  @ExcelProperty(value = "状态", order = 6, width = 10)
  private String status;

  /** 触发类型（SCHEDULE/MANUAL/API） */
  @ExcelProperty(value = "触发类型", order = 7, width = 12)
  private String triggerType;

  /** 执行节点 ID */
  @ExcelProperty(value = "执行节点", order = 8, width = 15)
  private String execNodeId;

  /** 分片索引 */
  @ExcelProperty(value = "分片索引", order = 9, width = 10)
  private Integer shardIndex;

  /** 是否慢任务 */
  @ExcelProperty(value = "慢任务", order = 10, width = 8)
  private Boolean slow;

  /** 摘要错误信息（截断前 200 字符） */
  @ExcelProperty(value = "错误信息", order = 11, width = 40)
  private String errorMessage;

  /** 创建时间 */
  @ExcelProperty(value = "创建时间", order = 12, width = 20)
  private String createdAt;
}
