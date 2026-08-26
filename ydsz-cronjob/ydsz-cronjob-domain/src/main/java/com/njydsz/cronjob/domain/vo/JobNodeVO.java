package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * JobNode 视图对象。
 *
 * <p>用于 Controller 层返回调度节点数据，对应实体 {@link com.njydsz.cronjob.domain.entity.job.JobNode}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobNodeVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 节点 ID（hostname:port 或 hostname:pid） */
  private String nodeId;

  /** 应用名称 */
  private String appName;

  /** 节点角色（ydsz-job-scheduler 等） */
  private String nodeRole;

  /** 主机名 */
  private String host;

  /** 端口 */
  private Integer port;

  /** 最后心跳时间 */
  private LocalDateTime lastHeartbeat;

  /** 节点状态: ONLINE 在线 / OFFLINE 离线 / DRAINING 排空退出中 */
  private String nodeStatus;

  /** CPU 使用率（百分比，0-100） */
  private BigDecimal cpuUsage;

  /** 内存使用率（百分比，0-100） */
  private BigDecimal memUsagePct;

  /** 当前正在执行的任务数 */
  private Integer runningCount;

  /** 加权平均响应时长（毫秒）：DB ping 延迟的指数移动平均，用于节点健康评估 */
  private Long responseTimeMs;

  /** 连续失败次数：心跳/健康检查连续失败次数，超过阈值触发自动隔离 */
  private Integer consecutiveFailures;

  /** 节点标签（JSON，用于任务亲和性选择） */
  private String tags;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
