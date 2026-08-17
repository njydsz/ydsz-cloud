package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * 远程派发配置（P1-4）。
 *
 * <p>Leader 节点将分片任务通过 HTTP 派发到选定的执行器节点， 实现真正的分布式分片执行（对标 XXL-Job / PowerJob 的远程派发）。
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

  /** 是否启用远程派发（false=所有分片在 Leader 本地执行，兼容旧行为） */
  private boolean enabled = true;

  /** HTTP 连接超时（秒） */
  private int connectTimeoutSeconds = 5;

  /** HTTP 请求超时（秒，包含连接+读取+执行） */
  private int requestTimeoutSeconds = 60;

  /** 远程派发失败时是否降级到 Leader 本地执行（true=保证分片不丢失） */
  private boolean fallbackToLocal = true;
}
