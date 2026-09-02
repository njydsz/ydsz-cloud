package com.njydsz.cronjob.server.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 节点配置（P1-3 补建：配置类拆分后 JobNodeHeartbeat 引用缺口）。
 *
 * <p>描述本节点在 {@code ydsz_job_node} 注册表与心跳上报中的身份与频率，供
 * {@code JobNodeHeartbeat} 构建节点信息与心跳循环使用。
 *
 * <h3>P1-12: 配置校验</h3>
 *
 * <p>通过 JSR-380 注解声明约束，启动时自动校验。
 *
 * @author ydsz-team
 * @since 26.09.01
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
  @NotBlank(message = "节点应用名 appName 不能为空")
  private String appName = DEFAULT_APP_NAME;

  /** 服务端口（注册到 ydsz_job_node.port） */
  @Min(value = 1, message = "服务端口 port 必须在 1-65535 之间")
  @Max(value = 65535, message = "服务端口 port 必须在 1-65535 之间")
  private int port = DEFAULT_PORT;

  /**
   * 心跳间隔（毫秒）。
   *
   * <p>P1-12: 必须在 1000ms~60000ms 之间，过短导致 DB 写入压力，过长导致故障检测延迟。
   */
  @Min(value = 1000, message = "心跳间隔 heartbeatIntervalMs 不能小于 1000ms")
  @Max(value = 60000, message = "心跳间隔 heartbeatIntervalMs 不能大于 60000ms")
  private long heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS;

  /** P1-1: 节点健康检查配置 */
  private NodeHealthConfig nodeHealth = new NodeHealthConfig();
}
