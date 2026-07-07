package com.njydsz.pmis.project.service;

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
     * @return EVM 指标列表
     */
    List<Map<String, Object>> evmReport(String initiationId);

    /**
     * 人效排行榜（按可计费利用率倒序，默认近 3 个月）
     *
     * @param top 返回 Top N
     * @return 人效排行榜列表
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
     * @return 人效排行榜列表
     */
    List<Map<String, Object>> utilizationRank(int top, LocalDate from, LocalDate to, String department);

    /**
     * 单员工可计费利用率
     *
     * @param employeeId 员工 ID
     * @param from       起始日期（含）
     * @param to         结束日期（含）
     * @return 利用率数据
     */
    Map<String, Object> utilizationOf(String employeeId, LocalDate from, LocalDate to);

    /**
     * 事业部级可计费利用率
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return 事业部利用率列表
     */
    List<Map<String, Object>> utilizationByDepartment(LocalDate from, LocalDate to);

    /**
     * Bench 闲置成本报表（近 N 天）
     *
     * @return Bench 闲置成本列表
     */
    List<Map<String, Object>> benchCostReport();

    /**
     * Bench 闲置成本报表（自定义时间窗口）
     *
     * @param from 起始日期（含）
     * @param to   结束日期（含）
     * @return Bench 闲置成本列表
     */
    List<Map<String, Object>> benchCostReport(LocalDate from, LocalDate to);

    /**
     * 双费率利润对比表
     *
     * <p>对比内部成本（成本费率）与外部收费（收入费率）的差额。
     *
     * @param period 期间（YYYY-MM）
     * @return 双费率利润对比列表
     */
    List<Map<String, Object>> dualRateProfitCompare(String period);

    /**
     * 资源负载甘特图数据
     *
     * <p>返回每个项目 × 人员的 时间段 + allocation 列表。
     *
     * @param initiationId 项目 ID
     * @return 甘特图数据列表
     */
    List<Map<String, Object>> resourceGantt(String initiationId);

    /**
     * 项目风险预警看板
     *
     * @return 风险预警列表
     */
    List<Map<String, Object>> riskDashboard();

    /**
     * 项目风险矩阵热力图（P2-2 体验增强）
     *
     * <p>基于 probability × impact 二维矩阵（各 3 档：LOW / MEDIUM / HIGH）聚合每个格子的风险数与项目数，
     * 用于前端 ECharts heatmap 渲染。同时返回：
     * <ul>
     *   <li>cellProjectIds — 该格子下风险涉及的项目 ID 列表（用于下钻）</li>
     *   <li>byType — 按 riskType 维度的风险数（用于堆叠辅助图）</li>
     *   <li>summary — 总体风险数 / 高风险数 / 中风险数 / 低风险数 / 涉及项目数</li>
     * </ul>
     *
     * <p>概率×影响等级映射遵循 RiskScoreEvaluator：
     * LOW*LOW/MEDIUM*LOW=LOW；其它中风险；HIGH*HIGH=HIGH。
     *
     * @param initiationId 项目 ID（可选；为空时全局统计）
     * @param riskType     风险类型过滤（SCOPE/SCHEDULE/COST/QUALITY/RESOURCE/EXTERNAL/OTHER，可空）
     * @param status       风险状态过滤（OPEN/IN_PROGRESS/CLOSED 等，可空）
     * @return 风险矩阵数据
     */
    Map<String, Object> riskMatrix(String initiationId, String riskType, String status);

    /**
     * 资源占用趋势图（双 Y 轴，P2-3 体验增强）
     *
     * <p>输出按月（YYYY-MM）聚合的资源占用与可计费利用率趋势：
     * <ul>
     *   <li>左 Y 轴 — 总工时 / 可计费工时 / 加班工时（柱状图）</li>
     *   <li>右 Y 轴 — 可计费利用率（折线图，单位 %）</li>
     * </ul>
     *
     * <p>支持按时间窗口和事业部过滤；数据源为 time_entry 聚合，异常时降级为空结构。
     *
     * @param from        起始日期（含）
     * @param to          结束日期（含）
     * @param department  事业部过滤（可空）
     * @return 资源占用趋势数据
     */
    Map<String, Object> resourceUtilizationTrend(LocalDate from, LocalDate to, String department);

    /**
     * 项目健康仪表盘（P2-5 体验增强）
     *
     * <p>综合 CPI / SPI / 毛利率 三维度计算每个项目的健康度评分（0-100）：
     * <ul>
     *   <li>score = cpi * 50 + spi * 30 + marginScore * 20</li>
     *   <li>marginScore = max(0, min(100, margin * 200)) — 50% 毛利率对应 100 分</li>
     *   <li>健康度等级：GREEN >= 80 / YELLOW >= 60 / RED < 60</li>
     * </ul>
     *
     * <p>数据源：EVM 最新测量（aggregateHealthByInitiation）+ ProfitSnapshot 最新快照。
     * 数据缺失时降级为 0 评分（健康度等级 = UNKNOWN）。
     *
     * @param initiationIds 可选项目 ID 列表（空 = 全局）
     * @param health        可选健康度过滤（GREEN/YELLOW/RED/UNKNOWN）
     * @return 项目健康仪表盘数据
     */
    Map<String, Object> projectHealthDashboard(List<String> initiationIds, String health);
}
