package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.workflow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.FlowAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批数据分析服务实现（P2-2）。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAnalyticsServiceImpl implements FlowAnalyticsService {

    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowRunTaskMapper runTaskMapper;

    @Override
    public Map<String, Object> overview(LocalDateTime startTime, LocalDateTime endTime, String tenantId) {
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();

        // 历史任务统计
        LambdaQueryWrapper<FlowHisTaskDO> hisWrapper = new LambdaQueryWrapper<FlowHisTaskDO>()
                .eq(FlowHisTaskDO::getTenantId, tid)
                .eq(FlowHisTaskDO::getDeleted, 0);
        if (startTime != null) {
            hisWrapper.ge(FlowHisTaskDO::getFinishAt, startTime);
        }
        if (endTime != null) {
            hisWrapper.le(FlowHisTaskDO::getFinishAt, endTime);
        }
        long totalHis = hisTaskMapper.selectCount(hisWrapper);

        long completedCount = hisTaskMapper.selectCount(
                new LambdaQueryWrapper<FlowHisTaskDO>()
                        .eq(FlowHisTaskDO::getTenantId, tid)
                        .eq(FlowHisTaskDO::getTaskStatus, "COMPLETED")
                        .eq(FlowHisTaskDO::getDeleted, 0)
                        .ge(startTime != null, FlowHisTaskDO::getFinishAt, startTime)
                        .le(endTime != null, FlowHisTaskDO::getFinishAt, endTime)
        );
        long rejectedCount = hisTaskMapper.selectCount(
                new LambdaQueryWrapper<FlowHisTaskDO>()
                        .eq(FlowHisTaskDO::getTenantId, tid)
                        .eq(FlowHisTaskDO::getTaskStatus, "REJECTED")
                        .eq(FlowHisTaskDO::getDeleted, 0)
                        .ge(startTime != null, FlowHisTaskDO::getFinishAt, startTime)
                        .le(endTime != null, FlowHisTaskDO::getFinishAt, endTime)
        );

        // 待办数
        long pendingCount = runTaskMapper.selectCount(
                new LambdaQueryWrapper<FlowRunTaskDO>()
                        .eq(FlowRunTaskDO::getTenantId, tid)
                        .eq(FlowRunTaskDO::getDeleted, 0)
                        .in(FlowRunTaskDO::getTaskStatus, "TODO", "CLAIMED")
        );

        // 超期数（dueAt < now 且未完成）
        long overdueCount = runTaskMapper.selectCount(
                new LambdaQueryWrapper<FlowRunTaskDO>()
                        .eq(FlowRunTaskDO::getTenantId, tid)
                        .eq(FlowRunTaskDO::getDeleted, 0)
                        .in(FlowRunTaskDO::getTaskStatus, "TODO", "CLAIMED")
                        .lt(FlowRunTaskDO::getDueAt, LocalDateTime.now())
        );

        // 驳回率
        double rejectionRate = totalHis > 0 ? (double) rejectedCount / totalHis : 0.0;

        // 平均耗时（复用已有的节点统计方法，取所有流程的平均值）
        List<Map<String, Object>> efficiencyData = hisTaskMapper.selectFlowEfficiencyComparison(tid, startTime, endTime);
        double avgDurationMs = 0;
        long totalDuration = 0;
        long totalCompleted = 0;
        for (Map<String, Object> row : efficiencyData) {
            Object avgDur = row.get("avgDurationMs");
            Object compCnt = row.get("completedCount");
            if (avgDur != null && compCnt != null) {
                long dur = ((Number) avgDur).longValue();
                long cnt = ((Number) compCnt).longValue();
                totalDuration += dur * cnt;
                totalCompleted += cnt;
            }
        }
        if (totalCompleted > 0) {
            avgDurationMs = (double) totalDuration / totalCompleted;
        }

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
        // 简单实现：复用 flowEfficiencyComparison 并按时间粒度在前端聚合
        // 完整实现需要 SQL GROUP BY date_trunc
        String tid = tenantId != null ? tenantId : TenantContext.getTenantId();
        List<Map<String, Object>> data = hisTaskMapper.selectFlowEfficiencyComparison(tid, startTime, endTime);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("granularity", granularity != null ? granularity : "DAY");
        result.put("data", data);
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        return result;
    }
}
