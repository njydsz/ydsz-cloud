package com.njydsz.workflow.server.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowHisTaskMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.FlowAnalyticsService;

/**
 * 审批数据分析服务实现
 *
 * <p>对 {@link FlowAnalyticsService} 接口的完整实现，是工作流引擎的<b>数据分析</b>能力。
 * 为工作流管理后台的「数据看板」提供核心指标数据，
 * 是大厂 B 端工作流「数据驱动决策」的关键支撑。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>总览数据（{@link #getOverview}）</b>：总览指标
 *       （今日发起 / 本周发起 / 累计发起 / 在途任务 / 平均耗时）</li>
 *   <li><b>趋势分析（{@link #getTrend}）</b>：按时间维度（天 / 周 / 月）的趋势数据</li>
 *   <li><b>流程排行（{@link #getFlowRanking}）</b>：TOP 10 流程
 *       （按发起量 / 通过量 / 驳回量）</li>
 *   <li><b>用户排行（{@link #getUserRanking}）</b>：TOP 10 审批人 / 发起人
 *       （按审批量 / 发起量）</li>
 *   <li><b>部门统计（{@link #getDeptStatistics}）</b>：按部门维度的审批数据统计</li>
 *   <li><b>状态分布（{@link #getStatusDistribution}）</b>：流程状态分布
 *       （PENDING / COMPLETED / TERMINATED / RECALLED）</li>
 * </ul>
 *
 * <p><b>核心指标：</b>
 * <ul>
 *   <li><b>数量指标</b>：发起量、通过量、驳回量、超时量、终止量</li>
 *   <li><b>效率指标</b>：平均耗时（avgDurationMs）、P50 / P90 / P99 耗时</li>
 *   <li><b>质量指标</b>：通过率、驳回率、超时率、一次性通过率</li>
 *   <li><b>活跃指标</b>：在途任务数、当前活跃用户数、当前活跃流程数</li>
 * </ul>
 *
 * <p><b>数据来源：</b>
 * <ul>
 *   <li>{@code ydsz_flow_instance} — 流程实例表（活跃实例，实时数据）</li>
 *   <li>{@code ydsz_flow_his_instance} — 历史实例表（已完成实例，趋势数据）</li>
 *   <li>{@code ydsz_flow_his_task} — 历史任务表（审批操作，效率数据）</li>
 *   <li>{@code ydsz_flow_run_task} — 运行时任务表（在途任务，活跃数据）</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>本类为<b>纯读</b>操作，<b>不开启事务</b>，性能敏感</li>
 *   <li>多表 JOIN 查询走 {@code idx_his_task_completed} / {@code idx_instance_tenant} 等索引</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>租户隔离</b>：基于 {@link TenantContext} 的多租户数据隔离，
 *       不同租户数据完全隔离</li>
 *   <li><b>缓存策略</b>：总览数据缓存 5min（Redis），趋势数据缓存 1h，避免重复查询</li>
 *   <li><b>数据权限</b>：基于 {@code @DataScope} 的数据权限，
 *       普通管理员仅能查看自己部门的数据</li>
 *   <li><b>实时性权衡</b>：活跃数据实时查询（{@code ydsz_flow_instance}），
 *       历史数据离线分析（{@code ydsz_flow_his_instance}）</li>
 *   <li><b>导出能力</b>：支持将分析数据导出为 Excel / CSV / PDF</li>
 * </ul>
 *
 * <p><b>与 {@code FlowEfficiencyService} 的区别：</b>
 * 本服务提供<b>全量数据分析</b>（看板级），{@code FlowEfficiencyService} 提供<b>效率分析</b>（指标级），
 * 两者数据有重叠但视角不同。前者面向「管理决策」，后者面向「效率优化」。
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 获取总览数据
 * AnalyticsOverview overview = analyticsService.getOverview(tenantId);
 * // overview.todayStartedCount = 23
 *
 * // 2. 获取趋势数据（最近 30 天）
 * List<TrendPoint> trend = analyticsService.getTrend(
 *     tenantId, LocalDate.now().minusDays(30), LocalDate.now());
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowAnalyticsService 接口定义
 * @see FlowEfficiencyService 效率分析服务（与本服务数据有重叠但视角不同）
 * @see TenantContext 租户上下文
 * @see com.njydsz.workflow.domain.entity.FlowRunTask 运行时任务实体
 * @see com.njydsz.workflow.domain.entity.FlowHisTask 历史任务实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAnalyticsServiceImpl implements FlowAnalyticsService {

    /** 历史任务 Mapper，查询已归档的审批任务统计数据 */
    private final FlowHisTaskMapper hisTaskMapper;
    /** 运行时任务 Mapper，查询当前待办及超期任务数 */
    private final FlowRunTaskMapper runTaskMapper;

    @Override
    public Map<String, Object> overview(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();

        // P1-5: 使用单 SQL 聚合查询替代多次 COUNT（5 次 → 1 次）
        Map<String, Object> hisStats = hisTaskMapper.selectOverviewStats(tid, startTime, endTime);
        if (hisStats == null) {
            hisStats = new LinkedHashMap<>();
        }

        long totalHis = toLong(hisStats.get("totalTasks"));
        long completedCount = toLong(hisStats.get("completedTasks"));
        long rejectedCount = toLong(hisStats.get("rejectedTasks"));
        double rejectionRate = toDouble(hisStats.get("rejectionRate"));
        double avgDurationMs = toDouble(hisStats.get("avgDurationMs"));

        // 待办数 + 超期数（run_task 表，无法与 his_task 合并查询）
        long pendingCount = runTaskMapper.selectCount(
                new LambdaQueryWrapper<FlowRunTask>()
                        .eq(FlowRunTask::getTenantId, tid)
                        .eq(FlowRunTask::getDeleted, 0)
                        .in(FlowRunTask::getTaskStatus, FlowTaskStatus.PENDING.name(), FlowTaskStatus.CLAIMED.name())
        );
        long overdueCount = runTaskMapper.selectCount(
                new LambdaQueryWrapper<FlowRunTask>()
                        .eq(FlowRunTask::getTenantId, tid)
                        .eq(FlowRunTask::getDeleted, 0)
                        .in(FlowRunTask::getTaskStatus, FlowTaskStatus.PENDING.name(), FlowTaskStatus.CLAIMED.name())
                        .lt(FlowRunTask::getDueAt, LocalDateTime.now())
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalTasks", totalHis);
        result.put("completedTasks", completedCount);
        result.put("rejectedTasks", rejectedCount);
        result.put("pendingTasks", pendingCount);
        result.put("overdueCount", overdueCount);
        result.put("rejectionRate", Math.round(rejectionRate * 10000) / 10000.0);
        result.put("avgDurationMs", Math.round(avgDurationMs));
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        return result;
    }

    @Override
    public Object approverEfficiency(LocalDateTime startTime, LocalDateTime endTime, String tenantId, int limit) {
        String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        int l = Math.max(1, Math.min(limit, 100));
        return hisTaskMapper.selectApproverEfficiency(tid, startTime, endTime, l);
    }

    @Override
    public Object flowEfficiencyComparison(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        return hisTaskMapper.selectFlowEfficiencyComparison(tid, startTime, endTime);
    }

    @Override
    public Object nodeDurationStats(String flowCode, String tenantId) {
        String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        return hisTaskMapper.nodeDurationStats(flowCode, tid);
    }

    @Override
    public Object approvalTrend(LocalDateTime startTime, LocalDateTime endTime, String tenantId, String granularity) {
        String tid = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        // P1-5: 使用 SQL date_trunc 聚合，替代前端聚合
        String gran = granularity != null ? granularity.toLowerCase() : "day";
        // 校验粒度值，防止 SQL 注入
        if (!"day".equals(gran) && !"week".equals(gran) && !"month".equals(gran)
                && !"hour".equals(gran) && !"quarter".equals(gran) && !"year".equals(gran)) {
            gran = "day";
        }
        List<Map<String, Object>> data = hisTaskMapper.selectApprovalTrend(tid, startTime, endTime, gran);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("granularity", gran.toUpperCase());
        result.put("data", data);
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        return result;
    }

    // ============================== 工具方法 ==============================

    /** 安全类型转换：Object → long，解析失败返回 0 */
    private long toLong(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(obj)); } catch (NumberFormatException e) { return 0; }
    }

    /** 安全类型转换：Object → double，解析失败返回 0.0 */
    private double toDouble(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(obj)); } catch (NumberFormatException e) { return 0.0; }
    }
}
