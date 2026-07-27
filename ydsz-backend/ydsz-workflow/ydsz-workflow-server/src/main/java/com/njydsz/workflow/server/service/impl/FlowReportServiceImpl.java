package com.njydsz.workflow.server.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.workflow.server.engine.FlowClusterLockHelper;

import org.springframework.stereotype.Service;

import com.njydsz.workflow.server.service.FlowAnalyticsService;
import com.njydsz.workflow.server.service.FlowNotificationService;
import com.njydsz.workflow.server.service.FlowReportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P2-4: 审批数据周报/月报服务实现
 *
 * <p>每周一 9:00 自动生成并推送周报，每月 1 号 9:00 自动生成并推送月报。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowReportServiceImpl implements FlowReportService {

    private final FlowAnalyticsService analyticsService;
    @Lazy
    private final FlowNotificationService notificationService;
    /** 集群锁：避免多节点重复推送周报/月报 */
    private final FlowClusterLockHelper clusterLockHelper;

    /**
     * 定时推送周报：每周一 9:00
     *
     * <p>集群幂等：通过 {@link FlowClusterLockHelper#tryRun} 加分布式锁，
     * 多节点部署时仅一个节点执行推送，避免重复发送。
     */
    @Scheduled(cron = "0 0 9 ? * MON")
    public void scheduledWeeklyReport() {
        clusterLockHelper.tryRun("report:weekly", 600, () -> {
            try {
                sendWeeklyReport("1");
                log.info("[FlowReport] 周报推送完成");
            } catch (Exception e) {
                log.error("[FlowReport] 周报推送失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 定时推送月报：每月 1 号 9:00
     *
     * <p>集群幂等：通过 {@link FlowClusterLockHelper#tryRun} 加分布式锁，
     * 多节点部署时仅一个节点执行推送，避免重复发送。
     */
    @Scheduled(cron = "0 0 9 1 * ?")
    public void scheduledMonthlyReport() {
        clusterLockHelper.tryRun("report:monthly", 600, () -> {
            try {
                sendMonthlyReport("1");
                log.info("[FlowReport] 月报推送完成");
            } catch (Exception e) {
                log.error("[FlowReport] 月报推送失败: {}", e.getMessage(), e);
            }
        });
    }

    @Override
    public Map<String, Object> generateWeeklyReport(String tenantId) {
        String tid = tenantId != null ? tenantId : "1";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = now.with(LocalTime.MAX);
        // 上周一 00:00
        LocalDateTime startTime = now.minusWeeks(1)
                .with(DayOfWeek.MONDAY)
                .with(LocalTime.MIN);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "WEEKLY");
        report.put("period", startTime.toLocalDate() + " ~ " + endTime.toLocalDate());
        report.put("generatedAt", now);

        // 聚合核心指标
        Map<String, Object> overview = analyticsService.overview(startTime, endTime, tid);
        report.put("overview", overview);

        // 审批趋势（按天）
        Object trend = analyticsService.approvalTrend(startTime, endTime, tid, "DAILY");
        report.put("trend", trend);

        // 审批人效率 Top 5
        Object topApprovers = analyticsService.approverEfficiency(startTime, endTime, tid, 5);
        report.put("topApprovers", topApprovers);

        // 流程效率对比
        Object flowComparison = analyticsService.flowEfficiencyComparison(startTime, endTime, tid);
        report.put("flowComparison", flowComparison);

        return report;
    }

    @Override
    public Map<String, Object> generateMonthlyReport(String tenantId) {
        String tid = tenantId != null ? tenantId : "1";
        LocalDateTime now = LocalDateTime.now();
        // 上月 1 号 00:00 ~ 上月最后一天 23:59:59
        LocalDateTime startTime = now.minusMonths(1)
                .withDayOfMonth(1)
                .with(LocalTime.MIN);
        LocalDateTime endTime = now.withDayOfMonth(1)
                .minusDays(1)
                .with(LocalTime.MAX);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "MONTHLY");
        report.put("period", startTime.toLocalDate() + " ~ " + endTime.toLocalDate());
        report.put("generatedAt", now);

        // 聚合核心指标
        Map<String, Object> overview = analyticsService.overview(startTime, endTime, tid);
        report.put("overview", overview);

        // 审批趋势（按周）
        Object trend = analyticsService.approvalTrend(startTime, endTime, tid, "WEEKLY");
        report.put("trend", trend);

        // 审批人效率 Top 10
        Object topApprovers = analyticsService.approverEfficiency(startTime, endTime, tid, 10);
        report.put("topApprovers", topApprovers);

        // 流程效率对比
        Object flowComparison = analyticsService.flowEfficiencyComparison(startTime, endTime, tid);
        report.put("flowComparison", flowComparison);

        return report;
    }

    @Override
    public boolean sendWeeklyReport(String tenantId) {
        try {
            Map<String, Object> report = generateWeeklyReport(tenantId);
            String title = "审批周报 - " + report.get("period");
            String content = buildReportContent(report);
            // 推送给管理员（userId=admin 或通过配置获取管理员列表）
            notificationService.send("WORKFLOW", "admin", title, content,
                    Map.of("reportType", "WEEKLY", "period", report.get("period")));
            log.info("[FlowReport] 周报已推送: period={}", report.get("period"));
            return true;
        } catch (Exception e) {
            log.error("[FlowReport] 周报推送失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendMonthlyReport(String tenantId) {
        try {
            Map<String, Object> report = generateMonthlyReport(tenantId);
            String title = "审批月报 - " + report.get("period");
            String content = buildReportContent(report);
            notificationService.send("WORKFLOW", "admin", title, content,
                    Map.of("reportType", "MONTHLY", "period", report.get("period")));
            log.info("[FlowReport] 月报已推送: period={}", report.get("period"));
            return true;
        } catch (Exception e) {
            log.error("[FlowReport] 月报推送失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private String buildReportContent(Map<String, Object> report) {
        StringBuilder sb = new StringBuilder();
        sb.append("报告周期: ").append(report.get("period")).append("\n");
        sb.append("生成时间: ").append(report.get("generatedAt")).append("\n\n");

        Object overview = report.get("overview");
        if (overview instanceof Map<?, ?> rawMap) {
            Map<String, Object> om = MapUtils.toStringObjectMap(rawMap);
            sb.append("【核心指标】\n");
            for (Map.Entry<String, Object> entry : om.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        return sb.toString();
    }
}
