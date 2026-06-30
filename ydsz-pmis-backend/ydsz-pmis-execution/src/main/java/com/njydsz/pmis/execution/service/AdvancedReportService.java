package com.njydsz.pmis.execution.service;

import java.time.LocalDate;
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
     * 人效排行榜（按可计费利用率倒序，默认近 3 个月）
     */
    List<Map<String, Object>> utilizationRank(int top);

    /**
     * 人效排行榜（按可计费利用率倒序，自定义时间窗口）
     *
     * <p>基于工时数据计算 billable_hours / (total_hours - leave_hours) 作为可计费利用率，
     * 按职级内部成本率折算人效贡献金额，输出排行榜。
     *
     * @param top         返回 Top N
     * @param from        起始日期（含）
     * @param to          结束日期（含）
     * @param department  事业部过滤（可选）
     */
    List<Map<String, Object>> utilizationRank(int top, LocalDate from, LocalDate to, String department);

    /**
     * 单员工可计费利用率
     */
    Map<String, Object> utilizationOf(Long employeeId, LocalDate from, LocalDate to);

    /**
     * 事业部级可计费利用率
     */
    List<Map<String, Object>> utilizationByDepartment(LocalDate from, LocalDate to);

    /**
     * Bench 闲置成本报表（近 N 天）
     */
    List<Map<String, Object>> benchCostReport();

    /**
     * Bench 闲置成本报表（自定义时间窗口）
     */
    List<Map<String, Object>> benchCostReport(LocalDate from, LocalDate to);

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
