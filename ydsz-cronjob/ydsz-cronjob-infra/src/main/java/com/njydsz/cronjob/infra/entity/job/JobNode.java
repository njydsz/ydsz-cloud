package com.njydsz.cronjob.infra.entity.job;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 调度节点心跳实体（ydsz_job_node 表）。
 *
 * <p>每个 cronjob 实例启动时注册一条记录，定时（默认 10s）更新 last_heartbeat。 Leader 节点扫描时通过 last_heartbeat 判断节点是否在线。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_node")
public class JobNode extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 节点 ID（hostname:port 或 hostname:pid） */
  private String nodeId;

  /** 应用名称 */
  private String appName;

  /** 主机名 */
  private String host;

  /** 端口 */
  private Integer port;

  /** 最后心跳时间 */
  private LocalDateTime lastHeartbeat;

  /** 节点状态：ONLINE 在线 / OFFLINE 离线 / DRAINING 排空退出中 */
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
}
