package com.njydsz.agent.domain.runtime;

import java.util.List;
import java.util.Optional;

/**
 * Agent 运行时会话存储网关接口。
 *
 * <p>定义运行时会话的持久化操作，由基础设施层实现。
 * 用于运行时管理面板查询活跃会话、强制回收等场景。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public interface RuntimeSessionStore {

    /**
     * 注册或更新一个运行时会话。
     *
     * @param session 运行时会话对象，不可为 null
     */
    void save(RuntimeSession session);

    /**
     * 根据执行 ID 查询运行时会话。
     *
     * @param executionId 执行 ID
     * @return 包含会话的 Optional，未找到返回空
     */
    Optional<RuntimeSession> findByExecutionId(String executionId);

    /**
     * 查询所有活跃状态的会话（运行中或等待中）。
     *
     * @return 活跃会话列表，按开始时间倒序
     */
    List<RuntimeSession> findActiveSessions();

    /**
     * 查询指定租户下的所有活跃会话。
     *
     * @param tenantId 租户 ID
     * @return 该租户的活跃会话列表
     */
    List<RuntimeSession> findActiveSessionsByTenant(String tenantId);

    /**
     * 移除指定的运行时会话（执行完成或强制回收后清理）。
     *
     * @param executionId 执行 ID
     */
    void remove(String executionId);

    /**
     * 查询所有运行时会话（包括非活跃状态），按开始时间倒序。
     *
     * @param limit 返回数量上限
     * @return 会话列表
     */
    List<RuntimeSession> findAll(int limit);

    /**
     * 统计当前活跃会话数量。
     *
     * @return 活跃会话总数
     */
    long countActive();
}
