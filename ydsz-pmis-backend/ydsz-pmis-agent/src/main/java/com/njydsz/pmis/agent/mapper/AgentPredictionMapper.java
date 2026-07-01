package com.njydsz.pmis.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.agent.entity.AgentPredictionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AgentPredictionMapper extends BaseMapper<AgentPredictionDO> {

    AgentPredictionDO selectByTaskCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    List<AgentPredictionDO> selectByBiz(@Param("bizType") String bizType,
                                        @Param("bizId") Long bizId,
                                        @Param("agentType") String agentType);

    List<AgentPredictionDO> selectByAgentType(@Param("agentType") String agentType,
                                              @Param("alertLevel") String alertLevel,
                                              @Param("limit") Integer limit);

    List<Map<String, Object>> aggregateByType(@Param("tenantId") Long tenantId);

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
     * <p>用于驾驶舱"AI 执行耗时"分 Agent 趋势展示</p>
     */
    List<Map<String, Object>> selectDurationStatsByAgentType(@Param("from") java.time.LocalDateTime from,
                                                              @Param("to") java.time.LocalDateTime to,
                                                              @Param("tenantId") Long tenantId);
}
