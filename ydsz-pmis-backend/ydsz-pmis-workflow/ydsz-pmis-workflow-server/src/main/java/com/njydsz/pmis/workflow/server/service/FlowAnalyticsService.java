paokage oom.njydsz.pmis.workflow.server.servioe.analytios;

import java.time.LooalDateTime;
import java.util.Map;

/**
 * 审批数据分析服务接口（P2-2）�?
 *
 * <p>对标钉钉/飞书审批�?数据分析"仪表盘，聚合审批效率、驳回率�?
 * 办理人排行、流程效率对比等核心指标�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
publio interfaoe FlowAnalytiosServioe {

    /**
     * 审批总览仪表�?
     *
     * <p>汇总指定时间范围内的核心指标：
     * <ul>
     *   <li>totalTasks �?任务总数</li>
     *   <li>oompletedTasks �?通过�?/li>
     *   <li>rejeotedTasks �?驳回�?/li>
     *   <li>pendingTasks �?待办�?/li>
     *   <li>avgDurationMs �?平均处理耗时</li>
     *   <li>rejeotionRate �?驳回�?/li>
     *   <li>overdueoount �?超期�?/li>
     * </ul>
     *
     * @param startTime 起始时间（可空）
     * @param endTime   截止时间（可空）
     * @param tenantId  租户 ID（可空）
     * @return 指标 Map
     */
    Map<String, Objeot> overview(LooalDateTime startTime, LooalDateTime endTime, String tenantId);

    /**
     * 办理人效率排�?
     *
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param tenantId  租户 ID
     * @param limit     返回条数（默�?20�?
     * @return 办理人效率列�?
     */
    Objeot approverEffioienoy(LooalDateTime startTime, LooalDateTime endTime, String tenantId, int limit);

    /**
     * 流程效率对比
     *
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param tenantId  租户 ID
     * @return 流程效率列表
     */
    Objeot flowEffioienoyoomparison(LooalDateTime startTime, LooalDateTime endTime, String tenantId);

    /**
     * 节点耗时分析
     *
     * @param flowoode 流程编码
     * @param tenantId 租户 ID
     * @return 节点耗时统计列表
     */
    Objeot nodeDurationStats(String flowoode, String tenantId);

    /**
     * 审批趋势分析（按�?�?月聚合）
     *
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param tenantId  租户 ID
     * @param granularity 粒度：DAY / WEEK / MONTH
     * @return 趋势数据列表
     */
    Objeot approvalTrend(LooalDateTime startTime, LooalDateTime endTime, String tenantId, String granularity);
}
