package com.njydsz.pmis.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Agent 预测记录数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface AgentPredictionMapper extends BaseMapper<AgentPredictionDO> {

    /**
     * 根据任务编码查询预测记录。
     *
     * @param code 任务编码
     * @return 预测记录实体；不存在返回 null
     */
    AgentPredictionDO selectByTaskCode(@Param("code") String code);

    /**
     * 更新执行状态。
     *
     * @param id     记录 ID
     * @param status 目标状态码（AgentRunStatus.code）
     * @return 受影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 按业务维度查询预测记录列表。
     *
     * @param bizType   业务类型，可空
     * @param bizId     业务 ID，可空
     * @param agentType Agent 类型码，可空
     * @return 预测记录列表
     */
    List<AgentPredictionDO> selectByBiz(@Param("bizType") String bizType,
                                        @Param("bizId") Long bizId,
                                        @Param("agentType") String agentType);

    /**
     * 按 Agent 类型与告警等级查询最近的预测记录。
     *
     * @param agentType  Agent 类型码，可空
     * @param alertLevel 告警等级码，可空
     * @param limit      返回条数
     * @return 预测记录列表
     */
    List<AgentPredictionDO> selectByAgentType(@Param("agentType") String agentType,
                                              @Param("alertLevel") String alertLevel,
                                              @Param("limit") Integer limit);

    /**
     * 按 Agent 类型聚合计数（用于看板）。
     *
     * @param tenantId 租户 ID
     * @return 每种 Agent 类型对应的数量列表
     */
    List<Map<String, Object>> aggregateByType(@Param("tenantId") Long tenantId);

    /**
     * 按告警等级统计 Agent 记录数量。
     *
     * @param alertLevel 告警等级码，可空
     * @param agentType  Agent 类型码，可空
     * @param tenantId   租户 ID
     * @return 数量
     */
    long countByAlertLevel(@Param("alertLevel") String alertLevel,
                           @Param("agentType") String agentType,
                           @Param("tenantId") Long tenantId);

    /**
     * 计算 AI Agent 执行耗时统计 (P50/P90/P95/Max/Avg)
     *
     * <p>使用 PostgreSQL percentile_cont(0.5/0.9/0.95) WITHIN GROUP (ORDER BY cost_ms) 聚合
     * 统计; 避免在 Java 端收集全量数据计算 percentile (O(N log N) 内存压力)</p>
     *
     * @param agentType  Agent 类型 (可空, 表示全部)
     * @param from       起始时间 (可空)
     * @param to         结束时间 (可空)
     * @param tenantId   租户 (可空)
     * @return 统计结果: p50Ms, p90Ms, p95Ms, maxMs, avgMs, sampleCount
     */
    Map<String, Object> selectDurationStats(@Param("agentType") String agentType,
                                            @Param("from") java.time.LocalDateTime from,
                                            @Param("to") java.time.LocalDateTime to,
                                            @Param("tenantId") Long tenantId);

    /**
     * 按 Agent 类型分组, 计算每类 Agent 的耗时 P50/P95
     *
     * <p>用于驾驶舱"AI 执行耗时"分 Agent 趋势展示</p>
     *
     * @param from     起始时间，可空
     * @param to       结束时间，可空
     * @param tenantId 租户 ID，可空
     * @return 每类 Agent 的 P50/P95 耗时统计列表
     */
    List<Map<String, Object>> selectDurationStatsByAgentType(@Param("from") java.time.LocalDateTime from,
                                                              @Param("to") java.time.LocalDateTime to,
                                                              @Param("tenantId") Long tenantId);
}
