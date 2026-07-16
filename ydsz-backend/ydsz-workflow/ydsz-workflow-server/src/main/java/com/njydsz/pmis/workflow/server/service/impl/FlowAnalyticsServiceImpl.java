package com.njydsz.workflow.server.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.security.TenantContext;
import com.njydsz.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowHisTaskMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.FlowAnalyticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批数据分析服务实现（P2-2）。
 *
 * @since 1.8.0
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
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();

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
                new LambdaQueryWrapper<FlowRunTaskDO>()
                        .eq(FlowRunTaskDO::getTenantId, tid)
                        .eq(FlowRunTaskDO::getDeleted, 0)
                        .in(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.PENDING.name(), FlowTaskStatus.CLAIMED.name())
        );
        long overdueCount = runTaskMapper.selectCount(
                new LambdaQueryWrapper<FlowRunTaskDO>()
                        .eq(FlowRunTaskDO::getTenantId, tid)
                        .eq(FlowRunTaskDO::getDeleted, 0)
                        .in(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.PENDING.name(), FlowTaskStatus.CLAIMED.name())
                        .lt(FlowRunTaskDO::getDueAt, LocalDateTime.now())
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
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();
        int l = Math.max(1, Math.min(limit, 100));
        return hisTaskMapper.selectApproverEfficiency(tid, startTime, endTime, l);
    }

    @Override
    public Object flowEfficiencyComparison(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();
        return hisTaskMapper.selectFlowEfficiencyComparison(tid, startTime, endTime);
    }

    @Override
    public Object nodeDurationStats(String flowCode, String tenantId) {
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();
        return hisTaskMapper.nodeDurationStats(flowCode, tid);
    }

    @Override
    public Object approvalTrend(LocalDateTime startTime, LocalDateTime endTime, String tenantId, String granularity) {
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();
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
