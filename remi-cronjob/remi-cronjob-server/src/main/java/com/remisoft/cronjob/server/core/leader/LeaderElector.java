package com.remisoft.cronjob.server.core.leader;

import java.time.Duration;

/**
 * Leader 选举接口。
 *
 * <p>用于在 cronjob 集群中选举唯一 Leader 节点，由 Leader 节点负责扫描待触发任务并派发。
 * 其他节点（Follower）仅注册心跳，等待 Leader 派发任务。
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>{@link #tryAcquire(String, Duration)} 应为非阻塞，立即返回结果</li>
 *   <li>Leader 持有锁后应在 lease 到期前续期，避免误释放</li>
 *   <li>Leader 节点崩溃后，锁应在 lease 到期后自动释放，允许其他节点竞选</li>
 *   <li>实现应线程安全，支持多 role 并发竞选</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface LeaderElector {

    /**
     * 尝试获取指定 role 的 Leader 身份。
     *
     * @param role  角色（如 "remi-job-scheduler"）
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
}
