package com.njydsz.cronjob.server.core.connector;

import java.util.Map;
import lombok.Data;

/**
 * 连接器任务信息（P2-3）。
 *
 * <p>统一的任务信息模型，用于跨调度系统的任务导入/导出。 不同外部系统的任务模型差异通过字段映射转换为本结构。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ConnectorTaskInfo {
  /** 外部系统任务 ID */
  private String externalTaskId;

  /** 任务名称 */
  private String jobName;

  /** 任务分组 */
  private String jobGroup;

  /** CRON 表达式 */
  private String cronExpression;

  /** 任务类型（BEAN / HTTP / GLUE / SHELL） */
  private String jobType;

  /** 执行器/Handler 标识 */
  private String executorHandler;

  /** 执行参数（JSON） */
  private String paramsJson;

  /** 任务描述 */
  private String description;

  /** 路由策略 */
  private String routeStrategy;

  /** 阻塞策略 */
  private String blockStrategy;

  /** 超时时间（秒） */
  private Integer timeoutSeconds;

  /** 重试次数 */
  private Integer retryCount;

  /** 告警邮箱 */
  private String alertEmail;

  /** 额外属性（各系统特有字段） */
  private Map<String, String> extraProps;

  /** 源系统标识 */
  private String sourceSystem;
}
