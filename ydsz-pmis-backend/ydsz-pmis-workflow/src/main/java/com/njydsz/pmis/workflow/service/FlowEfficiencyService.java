package com.njydsz.pmis.workflow.service;

import java.util.List;
import java.util.Map;

/**
 * GAP-P1: 审批效率分析服务
 *
 * <p>提供审批运营数据看板所需的统计能力，对标钉钉/飞书审批的"效率分析"模块。
 * 数据来源为 {@code pmis_flow_his_task} 历史任务归档表。
 *
 * <p>核心指标：
 * <ul>
 *   <li>审批单量 — 时间段内完成的审批任务总数</li>
 *   <li>平均耗时 — 每个审批任务的平均处理时长（毫秒）</li>
 *   <li>代批率 — 非本人处理（委派/转办后由他人完成）的占比</li>
 *   <li>超期率 — 超过 SLA 配置时限的占比</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public interface FlowEfficiencyService {

    /**
     * 审批效率统计 — 单量/平均耗时/代批率/超期率
     *
     * @param tenantId  租户 ID
     * @param startTime 开始时间（格式 yyyy-MM-dd HH:mm:ss，可空）
     * @param endTime   结束时间（格式 yyyy-MM-dd HH:mm:ss，可空）
     * @return 统计结果 Map，包含 totalCount / avgDurationMs / proxyRate / overdueRate
     */
    Map<String, Object> efficiencyStats(Long tenantId, String startTime, String endTime);

    /**
     * 节点瓶颈排名 — 按平均耗时降序
     *
     * @param tenantId 租户 ID
     * @param flowCode 流程编码（可空，为空则统计所有流程）
     * @param limit    返回条数上限
     * @return 瓶颈节点列表，每行含 nodeCode / nodeName / avgDurationMs / count
     */
    List<Map<String, Object>> bottleneckRanking(Long tenantId, String flowCode, int limit);

    /**
     * 审批人效率排名 — 按处理量/平均耗时
     *
     * @param tenantId  租户 ID
     * @param startTime 开始时间（可空）
     * @param endTime   结束时间（可空）
     * @param limit     返回条数上限
     * @return 审批人排名列表，每行含 assigneeId / assigneeName / handleCount / avgDurationMs
     */
    List<Map<String, Object>> approverRanking(Long tenantId, String startTime, String endTime, int limit);

    /**
     * 审批趋势 — 按日/周/月聚合
     *
     * @param tenantId 租户 ID
     * @param interval 聚合粒度：DAY / WEEK / MONTH
     * @param startTime 开始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 趋势列表，每行含 timeLabel / count / avgDurationMs
     */
    List<Map<String, Object>> approvalTrend(Long tenantId, String interval, String startTime, String endTime);

    /**
     * 综合异常检测 — 检测卡单任务、高驳回率节点、长期运行实例
     *
     * <p>聚合三类异常检测结果，按优先级返回：
     * <ul>
     *   <li><b>STUCK</b>：任务在同一节点停留超过阈值时间（默认 24 小时）</li>
     *   <li><b>HIGH_REJECTION</b>：节点在最近 100 个任务中驳回率超过 50%</li>
     *   <li><b>LONG_RUNNING</b>：流程实例运行时间超过阈值天数（默认 7 天）</li>
     * </ul>
     *
     * @param tenantId        租户 ID
     * @param limit           返回条数上限
     * @param stuckHours      卡单阈值（小时），默认 24
     * @param longRunningDays 长期运行阈值（天），默认 7
     * @return 异常记录列表，每行含 type / 描述字段
     */
    List<Map<String, Object>> detectAnomalies(Long tenantId, int limit, int stuckHours, int longRunningDays);

    /**
     * 检测卡单任务 — 同一节点停留超过阈值时间的未完成任务
     *
     * @param tenantId   租户 ID
     * @param limit      返回条数上限
     * @param stuckHours 卡单阈值（小时）
     * @return 卡单任务列表，每行含 type=STUCK / taskId / nodeCode / nodeName / stuckHours / createdAt
     */
    List<Map<String, Object>> detectStuckTasks(Long tenantId, int limit, int stuckHours);

    /**
     * 检测高驳回率节点 — 最近 100 个任务中驳回率超过 50% 的节点
     *
     * @param tenantId 租户 ID
     * @return 高驳回率节点列表，每行含 type=HIGH_REJECTION / nodeCode / nodeName / totalCount / rejectedCount / rejectionRate
     */
    List<Map<String, Object>> detectHighRejectionNodes(Long tenantId);

    /**
     * 检测长期运行实例 — 运行时间超过阈值天数的实例
     *
     * @param tenantId        租户 ID
     * @param limit           返回条数上限
     * @param longRunningDays 长期运行阈值（天）
     * @return 长期运行实例列表，每行含 type=LONG_RUNNING / instanceId / flowCode / flowName / startAt / runningDays
     */
    List<Map<String, Object>> detectLongRunningInstances(Long tenantId, int limit, int longRunningDays);

    /**
     * P1: 流程健康度综合评分（0-100 分）
     *
     * <p>基于效率统计和异常检测的综合评分，对标钉钉/飞书审批的"健康度"看板。
     * 评分维度：
     * <ul>
     *   <li>超期率（30%）：overdueRate 越低越好，最高扣 30 分</li>
     *   <li>代批率（20%）：proxyRate 过高说明审批人不在线，最高扣 20 分</li>
     *   <li>平均耗时（20%）：avgDurationMs 越低越好，最高扣 20 分</li>
     *   <li>异常数（30%）：卡单/高驳回/长期运行实例数，最高扣 30 分</li>
     * </ul>
     *
     * <p>评级标准：
     * <ul>
     *   <li>EXCELLENT（优秀）：≥ 90 分</li>
     *   <li>GOOD（良好）：75-89 分</li>
     *   <li>FAIR（一般）：60-74 分</li>
     *   <li>POOR（较差）：< 60 分</li>
     * </ul>
     *
     * @param tenantId  租户 ID
     * @param startTime 开始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 评分结果，含 score(0-100) / level(EXCELLENT/GOOD/FAIR/POOR) / deductions(扣分明细)
     */
    Map<String, Object> healthScore(Long tenantId, String startTime, String endTime);
}
