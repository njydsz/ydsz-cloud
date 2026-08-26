package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * 远程派发配置（P1-4）。
 *
 * <p>Leader 节点将分片任务通过 HTTP 派发到选定的执行器节点，实现真正的分布式分片执行。
 *
 * <h3>工作流程</h3>
 *
 * <ol>
 *   <li>Leader 计算分片分配方案（ShardingStrategy）
 *   <li>本地分片：Leader 直接调用 executeShard
 *   <li>远程分片：Leader 通过 HTTP POST 调用执行器节点的 /cronjob/internal/execute
 *   <li>执行器节点接收请求后在本地执行，返回 logId
 * </ol>
 *
 * <p>故障场景处理：
 *
 * <ul>
 *   <li>HTTP 连接失败/超时：根据 fallbackToLocal 决定是否降级本地执行
 *   <li>执行器节点宕机：JobNodeReaper 故障转移释放分片锁并标记日志 FAILED
 *   <li>重复执行风险：由 Redis 分布式锁兜底，同一分片只有一个节点能执行
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RemoteConfig {

  /** 默认connectTimeoutSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;

  /** 默认requestTimeoutSeconds值（可被配置文件覆盖） */
  private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 60;

  /** 是否启用远程派发（false=所有分片在 Leader 本地执行，兼容旧行为） */
  private boolean enabled = true;

  /** 内部通信鉴权令牌（节点间 HTTP 派发的共享密钥，对应请求头 X-Ydsz-Internal-Token）。 为空表示不鉴权（仅限可信内网，生产环境建议配置）；非空时接收端强制校验。 */
  private String accessToken = "";

  /** HTTP 连接超时（秒） */
  private int connectTimeoutSeconds = DEFAULT_CONNECT_TIMEOUT_SECONDS;

  /** HTTP 请求超时（秒，包含连接+读取+执行） */
  private int requestTimeoutSeconds = DEFAULT_REQUEST_TIMEOUT_SECONDS;

  /** 远程派发失败时是否降级到 Leader 本地执行（true=保证分片不丢失） */
  private boolean fallbackToLocal = true;
}
