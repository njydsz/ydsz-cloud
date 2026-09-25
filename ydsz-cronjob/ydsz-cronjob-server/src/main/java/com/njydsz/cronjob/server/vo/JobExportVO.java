package com.njydsz.cronjob.server.vo;

import java.io.Serial;
import java.io.Serializable;

import com.njydsz.common.excel.annotation.ExcelProperty;

import lombok.Data;

/**
 * 任务 Excel 导出 VO
 *
 * <p>用于 ydsz-common-excel 映射任务列表到 Excel 列，仅包含运维排查最常用的字段。
 *
 * <p>流式导出方案：通过 {@link com.njydsz.common.excel.core.ExcelWriter} 分页写入，
 * 避免一次性加载全量数据到内存。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class JobExportVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 任务 ID */
  @ExcelProperty(value = "任务ID", order = 1, width = 22)
  private String id;

  /** 任务名称 */
  @ExcelProperty(value = "任务名称", order = 2, width = 25)
  private String jobName;

  /** 任务分组 */
  @ExcelProperty(value = "任务分组", order = 3, width = 15)
  private String jobGroup;

  /** 任务状态（NORMAL/PAUSED/STOPPED/ERROR/DELETED） */
  @ExcelProperty(value = "状态", order = 4, width = 12)
  private String status;

  /** 任务唯一标识 */
  @ExcelProperty(value = "任务KEY", order = 5, width = 25)
  private String jobKey;

  /** 处理器 */
  @ExcelProperty(value = "处理器", order = 6, width = 30)
  private String handler;

  /** Cron 表达式 */
  @ExcelProperty(value = "Cron表达式", order = 7, width = 20)
  private String cronExpression;

  /** 调度类型 */
  @ExcelProperty(value = "调度类型", order = 8, width = 12)
  private String scheduleType;

  /** 下次触发时间 */
  @ExcelProperty(value = "下次触发时间", order = 9, width = 20)
  private String nextFireTime;

  /** 上次触发时间 */
  @ExcelProperty(value = "上次触发时间", order = 10, width = 20)
  private String lastFireTime;

  /** 累计触发次数 */
  @ExcelProperty(value = "触发次数", order = 11, width = 10)
  private Long fireCount;

  /** 成功次数 */
  @ExcelProperty(value = "成功次数", order = 12, width = 10)
  private Long successCount;

  /** 失败次数 */
  @ExcelProperty(value = "失败次数", order = 13, width = 10)
  private Long failCount;

  /** 创建人 */
  @ExcelProperty(value = "创建人", order = 14, width = 12)
  private String createdBy;
}
