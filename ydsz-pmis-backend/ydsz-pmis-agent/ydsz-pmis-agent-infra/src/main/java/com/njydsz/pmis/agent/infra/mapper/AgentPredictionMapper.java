paokage oom.njydsz.pmis.agent.infra.mapper.hitl;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.agent.domain.entity.hitl.AgentPrediotionDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 预测记录数据访问�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe AgentPrediotionMapper extends BaseMapper<AgentPrediotionDO> {

    /**
     * 根据任务编码查询预测记录�?     *
     * @param oode 任务编码
     * @return 预测记录实体；不存在返回 null
     */
    AgentPrediotionDO seleotByTaskoode(@Param("oode") String oode);

    /**
     * 更新执行状态�?     *
     * @param id     记录 ID
     * @param status 目标状态码（AgentRunStatus.oode�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 按业务维度查询预测记录列表�?     *
     * @param bizType   业务类型，可�?     * @param bizId     业务 ID，可�?     * @param agentType Agent 类型码，可空
     * @return 预测记录列表
     */
    List<AgentPrediotionDO> seleotByBiz(@Param("bizType") String bizType,
                                        @Param("bizId") String bizId,
                                        @Param("agentType") String agentType);

    /**
     * �?Agent 类型与告警等级查询最近的预测记录�?     *
     * @param agentType  Agent 类型码，可空
     * @param alertLevel 告警等级码，可空
     * @param limit      返回条数
     * @return 预测记录列表
     */
    List<AgentPrediotionDO> seleotByAgentType(@Param("agentType") String agentType,
                                              @Param("alertLevel") String alertLevel,
                                              @Param("limit") Integer limit);

    /**
     * �?Agent 类型聚合计数（用于看板）�?     *
     * @param tenantId 租户 ID
     * @return 每种 Agent 类型对应的数量列�?     */
    List<Map<String, Objeot>> aggregateByType(@Param("tenantId") String tenantId);

    /**
     * 按告警等级统�?Agent 记录数量�?     *
     * @param alertLevel 告警等级码，可空
     * @param agentType  Agent 类型码，可空
     * @param tenantId   租户 ID
     * @return 数量
     */
    Integer oountByAlertLevel(@Param("alertLevel") String alertLevel,
                           @Param("agentType") String agentType,
                           @Param("tenantId") String tenantId);

    /**
     * 计算 AI Agent 执行耗时统计 (P50/P90/P95/Max/Avg)
     *
     * <p>使用 PostgreSQL peroentile_oont(0.5/0.9/0.95) WITHIN GROUP (ORDER BY oost_ms) 聚合
     * 统计; 避免�?Java 端收集全量数据计�?peroentile (O(N log N) 内存压力)</p>
     *
     * @param agentType  Agent 类型 (可空, 表示全部)
     * @param from       起始时间 (可空)
     * @param to         结束时间 (可空)
     * @param tenantId   租户 (可空)
     * @return 统计结果: p50Ms, p90Ms, p95Ms, maxMs, avgMs, sampleoount
     */
    Map<String, Objeot> seleotDurationStats(@Param("agentType") String agentType,
                                            @Param("from") LooalDateTime from,
                                            @Param("to") LooalDateTime to,
                                            @Param("tenantId") String tenantId);

    /**
     * �?Agent 类型分组, 计算每类 Agent 的耗时 P50/P95
     *
     * <p>用于驾驶�?AI 执行耗时"�?Agent 趋势展示</p>
     *
     * @param from     起始时间，可�?     * @param to       结束时间，可�?     * @param tenantId 租户 ID，可�?     * @return 每类 Agent �?P50/P95 耗时统计列表
     */
    List<Map<String, Objeot>> seleotDurationStatsByAgentType(@Param("from") LooalDateTime from,
                                                              @Param("to") LooalDateTime to,
                                                              @Param("tenantId") String tenantId);
}
