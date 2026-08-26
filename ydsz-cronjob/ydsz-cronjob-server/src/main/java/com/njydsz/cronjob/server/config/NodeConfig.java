package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * 节点配置（P1-3 补建：配置类拆分后 JobNodeHeartbeat 引用缺口）。
 *
 * <p>描述本节点在 {@code ydsz_job_node} 注册表与心跳上报中的身份与频率，供
 * {@code JobNodeHeartbeat} 构建节点信息与心跳循环使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class NodeConfig {

  /** 默认心跳间隔（毫秒，10s） */
  private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 10000L;

  /** 默认节点应用名（对应 Nacos 服务名） */
  private static final String DEFAULT_APP_NAME = "ydsz-cronjob";

  /** 默认端口（与 bootstrap server.port 对齐） */
  private static final int DEFAULT_PORT = 9006;

  /** 节点 ID（空时回退 hostname:port） */
  private String nodeId = "";

  /** 应用名（注册到 ydsz_job_node.app_name） */
  private String appName = DEFAULT_APP_NAME;

  /** 服务端口（注册到 ydsz_job_node.port） */
  private int port = DEFAULT_PORT;

  /** 心跳间隔（毫秒） */
  private long heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS;
}
