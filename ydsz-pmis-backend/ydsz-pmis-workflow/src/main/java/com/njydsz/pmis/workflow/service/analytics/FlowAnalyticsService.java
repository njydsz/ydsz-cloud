package com.njydsz.pmis.workflow.service.analytics;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审批数据分析服务接口（P2-2）。
 *
 * <p>对标钉钉/飞书审批的"数据分析"仪表盘，聚合审批效率、驳回率、
 * 办理人排行、流程效率对比等核心指标。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public interface FlowAnalyticsService {

    /**
     * 审批总览仪表盘
     *
     * <p>汇总指定时间范围内的核心指标：
     * <ul>
     *   <li>totalTasks — 任务总数</li>
     *   <li>completedTasks — 通过数</li>
     *   <li>rejectedTasks — 驳回数</li>
     *   <li>pendingTasks — 待办数</li>
     *   <li>avgDurationMs — 平均处理耗时</li>
     *   <li>rejectionRate — 驳回率</li>
     *   <li>overdueCount — 超期数</li>
     * </ul>
     *
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @param tenantId  租户 ID（可空）
     * @return 指标 Map
     */
    Map<String, Object> overview(LocalDateTime startTime, LocalDateTime endTime, String tenantId);

    /**
     * 办理人效率排行
     *
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param tenantId  租户 ID
     * @param limit     返回条数（默认 20）
     * @return 办理人效率列表
     */
    Object approverEfficiency(LocalDateTime startTime, LocalDateTime endTime, String tenantId, int limit);

    /**
     * 流程效率对比
     *
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param tenantId  租户 ID
     * @return 流程效率列表
     */
    Object flowEfficiencyComparison(LocalDateTime startTime, LocalDateTime endTime, String tenantId);

    /**
     * 节点耗时分析
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID
     * @return 节点耗时统计列表
     */
    Object nodeDurationStats(String flowCode, String tenantId);

    /**
     * 审批趋势分析（按天/周/月聚合）
     *
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param tenantId  租户 ID
     * @param granularity 粒度：DAY / WEEK / MONTH
     * @return 趋势数据列表
     */
    Object approvalTrend(LocalDateTime startTime, LocalDateTime endTime, String tenantId, String granularity);
}
