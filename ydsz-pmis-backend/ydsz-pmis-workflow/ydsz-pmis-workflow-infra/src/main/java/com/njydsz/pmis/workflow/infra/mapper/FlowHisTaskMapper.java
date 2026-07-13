package com.njydsz.pmis.workflow.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.FlowHisTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 历史任务 Mapper
 *
 * <p>对应 pmis_flow_his_task 表，归档已完成的流程任务，供已办查询与审计追溯。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface FlowHisTaskMapper extends BaseMapper<FlowHisTaskDO> {

    /**
     * 查用户已办（历史）
     */
    List<FlowHisTaskDO> selectDoneByAssignee(@Param("assigneeId") String assigneeId,
                                             @Param("tenantId") String tenantId);

    /**
     * 查用户已办（历史，真分页：LIMIT/OFFSET）
     *
     * @param assigneeId 办理人 ID
     * @param tenantId   租户 ID
     * @param offset     偏移量（从 0 开始）
     * @param limit      每页大小
     */
    List<FlowHisTaskDO> selectDoneByAssigneePage(@Param("assigneeId") String assigneeId,
                                                 @Param("tenantId") String tenantId,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    /**
     * 统计用户已办总数（用于分页计算总页数）
     */
    long countDoneByAssignee(@Param("assigneeId") String assigneeId,
                             @Param("tenantId") String tenantId);

    /**
     * 查某实例的所有历史
     */
    List<FlowHisTaskDO> selectByInstanceId(@Param("instanceId") String instanceId);

    /**
     * P2-31: 按节点统计平均耗时（GROUP BY node_code, node_name）
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（可空）
     * @return 每个节点一行统计：nodeCode, nodeName, avgDurationMs, count
     */
    List<Map<String, Object>> nodeDurationStats(@Param("flowCode") String flowCode,
                                                 @Param("tenantId") String tenantId);

    /**
     * P2-33: 多维筛选已办分页查询（真分页：LIMIT/OFFSET）
     *
     * @param assigneeId   办理人 ID（可空）
     * @param businessType 业务类型（可空）
     * @param flowCode     流程编码（可空）
     * @param startTime    完成时间下界（可空）
     * @param endTime      完成时间上界（可空）
     * @param tenantId     租户 ID（可空）
     * @param offset       偏移量（从 0 开始）
     * @param limit        每页大小
     */
    List<FlowHisTaskDO> selectDonePage(@Param("assigneeId") String assigneeId,
                                       @Param("businessType") String businessType,
                                       @Param("flowCode") String flowCode,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       @Param("tenantId") String tenantId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * P2-33: 多维筛选已办总数统计
     */
    long countDone(@Param("assigneeId") String assigneeId,
                   @Param("businessType") String businessType,
                   @Param("flowCode") String flowCode,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime,
                   @Param("tenantId") String tenantId);

    /**
     * P1-1: 查询实例经过的历史节点（去重，按首次完成时间排序），
     * 用于驳回时让用户选择驳回到任意历史节点。
     *
     * @param instanceId 流程实例 ID
     * @return 节点列表：nodeCode / nodeName / firstFinishAt / assigneeName
     */
    List<Map<String, Object>> listPassedNodes(@Param("instanceId") String instanceId);

    /**
     * P1-5: 查询同实例下已审批过（task_status=COMPLETED）的办理人 ID 列表（去重）。
     *
     * <p>用于跨节点办理人去重：排除已审批过的人员，支持"一人多环节只审批一次"。
     * 排除 assignee_id = '0'（SYSTEM_AUTO_PASS / SERVICE 节点等系统生成的记录）。
     *
     * @param instanceId 流程实例 ID
     * @return 已审批过的办理人 ID 列表（去重）
     */
    List<String> selectCompletedAssigneeIds(@Param("instanceId") String instanceId);

    /**
     * P2-4: 按办理人分组聚合效率统计（SQL 层 GROUP BY，避免 Java 层全表加载）
     *
     * @param tenantId  租户 ID（可空）
     * @param startTime finish_at 下界（可空）
     * @param endTime   finish_at 上界（可空）
     * @param limit     返回条数
     * @return 每个办理人一行：assigneeId / assigneeName / completedCount / avgDurationMs / totalDurationMs
     */
    List<Map<String, Object>> selectApproverEfficiency(@Param("tenantId") String tenantId,
                                                        @Param("startTime") LocalDateTime startTime,
                                                        @Param("endTime") LocalDateTime endTime,
                                                        @Param("limit") int limit);

    /**
     * P2-7: 流程效率对比 — 按流程编码分组聚合效率指标。
     *
     * <p>对标钉钉/飞书"流程效率对比"看板。聚合指标：
     * <ul>
     *   <li>totalCount — 任务总数（COMPLETED + REJECTED）</li>
     *   <li>completedCount — 通过数（COMPLETED）</li>
     *   <li>rejectedCount — 驳回数（REJECTED）</li>
     *   <li>rejectionRate — 驳回率 = rejectedCount / totalCount</li>
     *   <li>avgDurationMs — 平均处理耗时（仅 COMPLETED）</li>
     * </ul>
     *
     * @param tenantId  租户 ID（可空）
     * @param startTime finish_at 下界（可空）
     * @param endTime   finish_at 上界（可空）
     * @return 每个流程一行：flowCode / flowName / totalCount / completedCount / rejectedCount / rejectionRate / avgDurationMs
     */
    List<Map<String, Object>> selectFlowEfficiencyComparison(@Param("tenantId") String tenantId,
                                                              @Param("startTime") LocalDateTime startTime,
                                                              @Param("endTime") LocalDateTime endTime);

    /**
     * P1-5: 单 SQL 聚合概览统计（替代多次 COUNT 查询，5 次 → 1 次）。
     *
     * <p>使用 PostgreSQL 条件聚合（COUNT ... FILTER）一次性返回：
     * totalTasks / completedTasks / rejectedTasks / rejectionRate / avgDurationMs
     *
     * @param tenantId  租户 ID（可空）
     * @param startTime finish_at 下界（可空）
     * @param endTime   finish_at 上界（可空）
     * @return 单行统计结果 Map
     */
    Map<String, Object> selectOverviewStats(@Param("tenantId") String tenantId,
                                             @Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * P1-5: 审批趋势 — 按时间粒度分组聚合（date_trunc）。
     *
     * @param tenantId    租户 ID（可空）
     * @param startTime   finish_at 下界（可空）
     * @param endTime     finish_at 上界（可空）
     * @param granularity 时间粒度：day / week / month
     * @return 每个时间粒度一行：date / totalCount / completedCount / rejectedCount / avgDurationMs
     */
    List<Map<String, Object>> selectApprovalTrend(@Param("tenantId") String tenantId,
                                                    @Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("granularity") String granularity);
}
