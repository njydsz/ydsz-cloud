package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P1-1: 节点发现策略配置。
 *
 * <p>控制执行器节点发现方式：
 *
 * <ul>
 *   <li>{@code nacos}（默认）：基于 Nacos 服务发现，复用现有注册能力，无需维护心跳表
 *   <li>{@code db}：基于 ydsz_job_node 心跳表（向后兼容，需配合 JobNodeHeartbeat + JobNodeReaper）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class NodeDiscoveryConfig {

  /** 节点发现策略: nacos(Nacos服务发现, 默认) / db(心跳表) */
  private String type = "nacos";
}
