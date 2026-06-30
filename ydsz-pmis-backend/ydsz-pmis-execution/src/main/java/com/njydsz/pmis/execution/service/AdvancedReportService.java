package com.njydsz.pmis.execution.service;

import java.util.List;
import java.util.Map;

/**
 * 高级报表服务
 *
 * <p>提供 6 类高级报表：
 * <ul>
 *   <li>EVM 挣值管理报表</li>
 *   <li>可计费利用率分析报表（人效排行榜）</li>
 *   <li>Bench 闲置成本报表</li>
 *   <li>双费率利润对比表</li>
 *   <li>资源负载与调度报表（甘特数据）</li>
 *   <li>项目风险预警看板</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AdvancedReportService {

    /**
     * EVM 挣值管理报表
     *
     * @param initiationId 项目 ID
     */
    List<Map<String, Object>> evmReport(Long initiationId);

    /**
     * 人效排行榜（按可计费利用率倒序）
     */
    List<Map<String, Object>> utilizationRank(int top);

    /**
     * Bench 闲置成本报表
     */
    List<Map<String, Object>> benchCostReport();

    /**
     * 双费率利润对比表
     *
     * <p>对比内部成本（成本费率）与外部收费（收入费率）的差额。
     */
    List<Map<String, Object>> dualRateProfitCompare(String period);

    /**
     * 资源负载甘特图数据
     *
     * <p>返回每个项目 × 人员的 时间段 + allocation 列表。
     */
    List<Map<String, Object>> resourceGantt(Long initiationId);

    /**
     * 项目风险预警看板
     */
    List<Map<String, Object>> riskDashboard();
}
