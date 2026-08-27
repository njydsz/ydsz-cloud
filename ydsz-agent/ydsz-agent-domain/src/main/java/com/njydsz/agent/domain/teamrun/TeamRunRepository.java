package com.njydsz.agent.domain.teamrun;

import java.util.List;
import java.util.Optional;

/**
 * Team Run 仓储接口。
 *
 * <p>定义 Team Run 聚合根的持久化操作。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
public interface TeamRunRepository {

    /**
     * 保存 Team Run。
     *
     * @param teamRun Team Run 聚合根
     * @return 保存后的 Team Run 实例
     */
    TeamRun save(TeamRun teamRun);

    /**
     * 根据 ID 查询 Team Run。
     *
     * @param teamRunId Team Run ID
     * @return Team Run 实例（可能为空）
     */
    Optional<TeamRun> findById(String teamRunId);

    /**
     * 查询租户下所有活跃的 Team Run。
     *
     * @param tenantId 租户 ID
     * @return 活跃的 Team Run 列表
     */
    List<TeamRun> findActiveByTenant(String tenantId);

    /**
     * 查询租户下所有 Team Run。
     *
     * @param tenantId 租户 ID
     * @return Team Run 列表
     */
    List<TeamRun> findAllByTenant(String tenantId);

    /**
     * 删除 Team Run。
     *
     * @param teamRunId Team Run ID
     */
    void delete(String teamRunId);

    /**
     * 统计租户下 Team Run 数量。
     *
     * @param tenantId 租户 ID
     * @return Team Run 数量
     */
    long countByTenant(String tenantId);
}
