package com.njydsz.pmis.workflow.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;

/**
 * 流程实例 Mapper
 *
 * <p>对应 pmis_flow_instance 表，提供按业务关联查询、状态推进、发起人维度查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowInstanceMapper extends BaseMapper<FlowInstanceDO> {

    /**
     * 根据业务关联查实例（P1-2: 含 tenant_id 过滤 + 仅活跃状态）
     */
    FlowInstanceDO selectByBusiness(@Param("tenantId") String tenantId,
                                    @Param("businessType") String businessType,
                                    @Param("businessId") String businessId);

    /**
     * 状态更新
     */
    int updateStatus(@Param("id") String id,
                     @Param("flowStatus") String flowStatus,
                     @Param("currentNodeCode") String currentNodeCode,
                     @Param("currentNodeName") String currentNodeName,
                     @Param("endAt") LocalDateTime endAt,
                     @Param("durationMs") Long durationMs);

    /**
     * P2-18: 更新流程变量 JSON（用于持久化 terminate reason 等元信息）
     *
     * @param id       实例 ID
     * @param variable 流程变量 JSON
     */
    int updateVariable(@Param("id") String id,
                       @Param("variable") String variable);

    /**
     * 发起人维度查询
     */
    List<FlowInstanceDO> selectByInitiator(@Param("initiatorId") String initiatorId,
                                           @Param("flowStatus") String flowStatus);

    /**
     * P2-23: 实例多维分页查询
     *
     * <p>P1-3: 新增 {@code dataScopeFilter} 参数，支持数据权限 SQL 片段注入。
     *
     * @param businessType    业务类型（可选）
     * @param initiatorId     发起人 ID（可选）
     * @param flowStatus      流程状态（可选）
     * @param startTime       开始时间下界（可选）
     * @param endTime         开始时间上界（可选）
     * @param tenantId        租户 ID（可选）
     * @param dataScopeFilter 数据权限 SQL 片段（可选，由 DataScopeHelper.buildSqlFragment 生成）
     * @param offset          偏移量（从 0 开始）
     * @param limit           每页大小
     * @return 实例列表
     */
    List<FlowInstanceDO> selectPage(@Param("businessType") String businessType,
                                    @Param("initiatorId") String initiatorId,
                                    @Param("flowStatus") String flowStatus,
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime,
                                    @Param("tenantId") String tenantId,
                                    @Param("dataScopeFilter") String dataScopeFilter,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /**
     * P2-23: 实例多维分页计数
     *
     * @param businessType 业务类型（可选）
     * @param initiatorId  发起人 ID（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @param tenantId     租户 ID（可选）
     * @return 总数
     */
    long countPage(@Param("businessType") String businessType,
                   @Param("initiatorId") String initiatorId,
                   @Param("flowStatus") String flowStatus,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime,
                   @Param("tenantId") String tenantId,
                   @Param("dataScopeFilter") String dataScopeFilter);

    /**
     * 更新实例的 dueAt 字段（子流程超时用）
     *
     * @param id    实例 ID
     * @param dueAt 超时时间
     */
    int updateDueAt(@Param("id") String id,
                    @Param("dueAt") LocalDateTime dueAt);

    /**
     * 查询超期的子流程实例（dueAt < now 且状态为 RUNNING 且有 parentInstanceId）
     *
     * @param tenantId 租户 ID（可空）
     * @return 超期子流程实例列表
     */
    List<FlowInstanceDO> selectOverdueInstances(@Param("tenantId") String tenantId);

    /**
     * P2-4: 按 flow_status 分组计数（监控概览用，避免多次 count 查询）
     *
     * @param tenantId 租户 ID（可空）
     * @return 每种状态一行：flowStatus / cnt
     */
    List<Map<String, Object>> selectCountGroupByStatus(@Param("tenantId") String tenantId);

    /**
     * P2-4: 统计今日新增/完成实例数
     *
     * <p>今日新增按 start_at &gt;= 今日 00:00:00 过滤；
     * 今日完成按 end_at &gt;= 今日 00:00:00 过滤。
     *
     * @param tenantId 租户 ID（可空）
     * @return 单行：todayNewCount / todayCompletedCount
     */
    Map<String, Object> selectTodayCount(@Param("tenantId") String tenantId);

    /**
     * P2-4: 按流程编码分组统计实例数（监控分布图用）
     *
     * @param tenantId  租户 ID（可空）
     * @param startTime start_at 下界（可空）
     * @param endTime   start_at 上界（可空）
     * @return 每个流程一行：flowCode / flowName / cnt
     */
    List<Map<String, Object>> selectFlowTypeDistribution(@Param("tenantId") String tenantId,
                                                          @Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime);

    /**
     * P2-4: 按日期分组统计新增/完成实例数（监控趋势图用）
     *
     * <p>新增按 start_at 日期分组；完成按 end_at 日期分组；分别聚合后做外连接。
     * 实现采用两次 GROUP BY 后在 Java 层合并（避免 SQL FULL OUTER JOIN 复杂性）。
     *
     * @param tenantId  租户 ID（可空）
     * @param startTime start_at 下界
     * @param endTime   start_at 上界
     * @return 每天一行：date / newCount
     */
    List<Map<String, Object>> selectDailyNewCount(@Param("tenantId") String tenantId,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("endTime") LocalDateTime endTime);

    /**
     * P2-4: 按日期分组统计完成实例数
     *
     * @param tenantId  租户 ID（可空）
     * @param startTime end_at 下界
     * @param endTime   end_at 上界
     * @return 每天一行：date / completedCount
     */
    List<Map<String, Object>> selectDailyCompletedCount(@Param("tenantId") String tenantId,
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime);

    /**
     * P2-5: 统计某流程定义的在途实例数（flow_status = 'RUNNING'）。
     *
     * <p>用于变更影响分析：判断老版本定义是否还有未完成的实例。
     *
     * @param definitionId 流程定义 ID
     * @return 在途实例数
     */
    long countRunningByDefinition(@Param("definitionId") String definitionId);

    /**
     * P2-5: 按当前节点分组统计某流程定义的在途实例数。
     *
     * <p>用于变更影响分析：识别哪些节点有在途实例，评估节点变更的影响范围。
     *
     * @param definitionId 流程定义 ID
     * @return 每个节点一行：currentNodeCode / currentNodeName / cnt
     */
    List<Map<String, Object>> selectRunningGroupByNode(@Param("definitionId") String definitionId);
}
