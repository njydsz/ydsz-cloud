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
}
