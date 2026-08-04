package com.remisoft.message.server.service.core;

/**
 * 智能去重服务（P2-1）。
 *
 * <p>基于 Redis {@code SET NX EX} 原子操作实现短窗口去重：相同 dedupKey 的消息
 * 在配置的 TTL 秒内仅允许通过一次，超时后自动释放。
 *
 * <p>降级策略：Redis 不可用时 fail-open（放行），避免阻断业务。
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface DedupService {

    /**
     * 尝试获取去重锁（SET NX EX）。
     *
     * <p>调用方应在发送前调用本方法，传入由 {@code bizType:bizId:templateCode:receiver}
     * 或 {@code messageId} 构建的 dedupKey。返回 {@code true} 表示首次到达，允许发送；
     * 返回 {@code false} 表示窗口内重复，应跳过发送。
     *
     * <p>注意：
     * <ul>
     *   <li>dedupKey 为 null / 空白时直接返回 true（无去重维度，放行）</li>
     *   <li>去重总开关关闭时直接返回 true</li>
     *   <li>Redis 异常时 fail-open 返回 true 并记 WARN 日志</li>
     * </ul>
     *
     * @param dedupKey 去重键（由调用方构建，可为空）
     * @return true 表示非重复（允许发送），false 表示重复（应跳过）
     */
    boolean tryAcquire(String dedupKey);
}
