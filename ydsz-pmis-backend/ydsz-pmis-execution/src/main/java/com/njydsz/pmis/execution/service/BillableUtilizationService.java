package com.njydsz.pmis.execution.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 可计费利用率统计与考核
 *
 * <p>基于已审批工时（status=APPROVED）按人员/职级/月维度计算：
 * <ul>
 *   <li>totalHours   - 全部作业时长（小时）</li>
 *   <li>billableHours - 可计费工时（REGULAR/OVERTIME 且 billable=1）</li>
 *   <li>overtimeHours / leaveHours / trainingHours</li>
 *   <li>utilizationPct - 可计费利用率 = billable / total * 100%</li>
 *   <li>grade - 考核等级（EXCELLENT/GOOD/NORMAL/WARN/CRITICAL）</li>
 * </ul>
 *
 * <p>考核等级（行业标准）：
 * <ul>
 *   <li>EXCELLENT 优秀：≥ 85%</li>
 *   <li>GOOD      良好：70% ~ 85%</li>
 *   <li>NORMAL    合格：50% ~ 70%</li>
 *   <li>WARN      预警：30% ~ 50%（黄色预警）</li>
 *   <li>CRITICAL  严重：&lt; 30%（红色预警）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface BillableUtilizationService {

    /**
     * 按月聚合所有员工的利用率明细
     *
     * @param from 起始日期（含）
     * @param to 截止日期（含）
     * @return List 每行 = 一个员工一个月
     */
    List<Map<String, Object>> aggregate(LocalDate from, LocalDate to);

    /**
     * 个人利用率（汇总 from-to 区间）
     *
     * @param employeeId 员工 ID
     * @param from       起始日期（含）
     * @param to         截止日期（含）
     * @return 个人利用率数据
     */
    Map<String, Object> personal(Long employeeId, LocalDate from, LocalDate to);

    /**
     * 排行榜（按 utilizationPct 降序，取前 N）
     *
     * @param from 起始日期（含）
     * @param to   截止日期（含）
     * @param top  返回 Top N
     * @return 排行榜列表
     */
    List<Map<String, Object>> rank(LocalDate from, LocalDate to, int top);

    /**
     * 团队/公司整体均值
     *
     * @param from 起始日期（含）
     * @param to   截止日期（含）
     * @return 整体利用率数据
     */
    Map<String, Object> overall(LocalDate from, LocalDate to);

    /**
     * 扫描利用率预警：WARN/CRITICAL 的员工
     *
     * @param from 起始日期（含）
     * @param to   截止日期（含）
     * @return 预警员工列表
     */
    List<Map<String, Object>> scanAlerts(LocalDate from, LocalDate to);

    /**
     * 计算（不依赖数据库）：给一个 total/billable 数字直接得评估
     *
     * @param totalHours   总工时
     * @param billableHours 可计费工时
     * @return 利用率评估数据
     */
    Map<String, Object> evaluate(double totalHours, double billableHours);

    /**
     * 触发快照重算（供 scheduler 调用）
     *
     * <p>聚合 pmis_execution_time_entry（status=APPROVED）中指定周期的工时，
     * 写入 pmis_billable_utilization_snapshot。recomputeAll=true 时先按 period 软删再重写。
     *
     * @param period       yyyy-MM；为空时取上一月
     * @param recomputeAll 是否清空本周期所有快照后重算
     * @return 包含 affectedCount / period / recomputeAt / costMs 的结果
     */
    Map<String, Object> recompute(String period, boolean recomputeAll);

    /**
     * 读取最新一期快照均值（驾驶舱取数）
     *
     * <p>优先从快照表读取，无数据时实时聚合兜底，保证驾驶舱 KPI 永远有数。
     *
     * @param period 期间（yyyy-MM）
     * @return 快照均值数据
     */
    Map<String, Object> snapshotAverage(String period);
}
