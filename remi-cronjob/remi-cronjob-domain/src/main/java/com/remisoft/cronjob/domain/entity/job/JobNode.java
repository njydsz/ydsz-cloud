package com.remisoft.cronjob.domain.entity.job;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.remisoft.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 调度节点心跳实体（remi_job_node 表）。
 *
 * <p>每个 cronjob 实例启动时注册一条记录，定时（默认 10s）更新 last_heartbeat。
 * Leader 节点扫描时通过 last_heartbeat 判断节点是否在线。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("remi_job_node")
public class JobNode extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

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

    /** 节点标签（JSON，用于任务亲和性选择） */
    private String tags;
}
