package com.njydsz.cronjob.server.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * 多云/多集群任务漂移配置（P2-5）。
 *
 * <p>定义远程集群的连接信息，支持将任务从当前集群"漂移"到目标集群。
 * 漂移流程：源集群注销调度器 → 通过 HTTP 调用目标集群注册 → 更新 DB cluster 字段。
 *
 * <h3>配置示例</h3>
 *
 * <pre>{@code
 * ydsz:
 *   cronjob:
 *     multi-cluster:
 *       enabled: true
 *       clusters:
 *         beijing:
 *           base-url: "http://cronjob-bj.internal:8080"
 *           access-token: "xxx"
 *           connect-timeout-seconds: 5
 *           request-timeout-seconds: 30
 *         shanghai:
 *           base-url: "http://cronjob-sh.internal:8080"
 *           access-token: "yyy"
 *           connect-timeout-seconds: 5
 *           request-timeout-seconds: 30
 * }</pre>
 *
 * <h3>安全约束</h3>
 *
 * <ul>
 *   <li>跨集群调用必须携带 access-token，目标集群校验通过后才执行注册
 *   <li>漂移操作记录审计日志（module=cronjob, action=MIGRATE）
 *   <li>漂移前校验目标集群可达性，不可达时快速失败
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MultiClusterConfig {

  /** 是否启用多云漂移能力（默认 false，显式开启） */
  private boolean enabled = false;

  /** 远程集群配置映射（key = 集群名称，如 "beijing"、"shanghai"） */
  private Map<String, RemoteCluster> clusters = new HashMap<>();

  /**
   * 远程集群连接信息。
   */
  @Data
  public static class RemoteCluster {
    /** 集群基础 URL（含协议和端口，如 http://cronjob-bj.internal:8080） */
    private String baseUrl;

    /** 跨集群调用鉴权令牌（与目标集群 ydsz.cronjob.remote.access-token 一致） */
    private String accessToken = "";

    /** 默认 HTTP 连接超时（秒） */
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;

    /** 默认 HTTP 请求超时（秒） */
    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 30;

    /** HTTP 连接超时（秒） */
    private int connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;

    /** HTTP 请求超时（秒） */
    private int requestTimeoutSeconds = DEFAULT_REQUEST_TIMEOUT_SECONDS;
  }
}
