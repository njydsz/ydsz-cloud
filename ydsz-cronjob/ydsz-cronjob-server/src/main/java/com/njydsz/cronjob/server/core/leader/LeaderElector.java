package com.njydsz.cronjob.server.core.leader;

import java.time.Duration;

/**
 * Leader 选举接口。
 *
 * <p>用于在 cronjob 集群中选举唯一 Leader 节点，由 Leader 节点负责扫描待触发任务并派发。 其他节点（Follower）仅注册心跳，等待 Leader 派发任务。
 *
 * <h3>实现约束</h3>
 *
 * <ul>
 *   <li>{@link #tryAcquire(String, Duration)} 应为非阻塞，立即返回结果
 *   <li>Leader 持有锁后应在 lease 到期前续期，避免误释放
 *   <li>Leader 节点崩溃后，锁应在 lease 到期后自动释放，允许其他节点竞选
 *   <li>实现应线程安全，支持多 role 并发竞选
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface LeaderElector {

  /**
   * 尝试获取指定 role 的 Leader 身份。
   *
   * @param role 角色（如 "ydsz-job-scheduler"）
   * @param lease 租约时长（到期后自动释放，需在到期前 {@link #renew(String)} 续期）
   * @return true 获取成功；false 已被其他节点持有
   */
  boolean tryAcquire(String role, Duration lease);

  /**
   * 续期 Leader 租约。
   *
   * <p>应在 lease 到期前调用，否则锁会被自动释放。
   *
   * @param role 角色
   * @return true 续期成功；false 已不是 Leader（需重新竞选）
   */
  boolean renew(String role);

  /**
   * 判断当前节点是否为指定 role 的 Leader。
   *
   * @param role 角色
   * @return true 是 Leader
   */
  boolean isLeader(String role);

  /**
   * 主动释放 Leader 身份（优雅下线时调用）。
   *
   * @param role 角色
   */
  void release(String role);

  /**
   * 查询当前 Leader 节点标识。
   *
   * @param role 角色
   * @return Leader 节点 ID；无 Leader 时返回 null
   */
  String getCurrentLeader(String role);

  /**
   * 获取指定 role 的 Leader 任期号（epoch，即 fencing token）。
   *
   * <p>用于脑裂防护：每次 Leader 成功抢占（或重新抢占）时在 Redis 中单调递增任期号，
   * 派发方在扫描开始时捕获任期号，逐任务派发前比对——若任期号已变化，说明 Leader 身份
   * 已被其他节点接管（如 Redis 主从切换窗口内的双主场景），应立即中止本轮派发，防止双写。
   *
   * <p>默认返回 -1，表示实现不参与 fencing（兼容 {@link #tryAcquire(String, Duration)} 的
   * 最小实现与测试桩）；基于 Redis 的实现应覆盖此方法。
   *
   * @param role 角色
   * @return 当前节点持有的该 role 任期号；非 Leader 或实现不支持时返回 -1
   */
  default long getEpoch(String role) {
    return -1L;
  }
}
