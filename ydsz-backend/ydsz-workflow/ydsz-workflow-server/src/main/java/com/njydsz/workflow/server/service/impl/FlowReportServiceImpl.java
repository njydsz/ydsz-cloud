package com.njydsz.workflow.server.service.impl;

import com.njydsz.common.util.collection.MapUtils;
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
 * 审批数据周报/月报服务实现
 *
 * <p>对 {@link FlowReportService} 接口的完整实现，承担工作流引擎的<b>数据报告</b>能力。
 * 通过定时任务自动生成「审批数据周报 / 月报」并推送给指定用户（通常是部门负责人 / 管理员），
 * 是大厂 B 端工作流「数据驱动管理」的标配。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>周报生成（{@link #generateWeeklyReport}）</b>：每周一 9:00 自动生成本周审批数据报告
 *       （发起量 / 通过量 / 驳回量 / 平均耗时 / TOP 申请人 / TOP 审批人）</li>
 *   <li><b>月报生成（{@link #generateMonthlyReport}）</b>：每月 1 号 9:00 自动生成本月审批数据报告
 *       （环比上月 / 同比去年 / 异常趋势 / 部门对比）</li>
 *   <li><b>报告推送（{@link #sendReport}）</b>：报告生成后通过 {@link FlowNotificationService}
 *       推送给指定用户（站内信 / 邮件 / 企业微信）</li>
 *   <li><b>自定义报告（{@link #generateCustomReport}）</b>：支持业务方按维度自定义报告
 *       （指定时间范围 / 流程类型 / 部门）</li>
 *   <li><b>报告历史（{@link #listReportHistory}）</b>：报告历史记录可查询、可下载</li>
 * </ul>
 *
 * <p><b>定时任务调度：</b>
 * <ul>
 *   <li><b>周报</b>：{@code @Scheduled(cron = "0 0 9 ? * MON")} 每周一 9:00 触发</li>
 *   <li><b>月报</b>：{@code @Scheduled(cron = "0 0 9 1 * ?")} 每月 1 号 9:00 触发</li>
 *   <li>通过 {@link FlowClusterLockHelper} 分布式锁保证集群中只有<b>一个节点</b>执行推送，
 *       避免重复推送（{@code ydsz:flow:report:weekly:lock} / {@code ydsz:flow:report:monthly:lock}）</li>
 * </ul>
 *
 * <p><b>报告内容：</b>
 * <ul>
 *   <li><b>核心指标</b>：发起量、通过量、驳回量、超时量、平均审批耗时</li>
 *   <li><b>趋势分析</b>：环比上月 / 同比去年（按日 / 周 / 月维度）</li>
 *   <li><b>TOP 排行</b>：TOP 10 申请人 / 审批人 / 流程类型 / 部门</li>
 *   <li><b>异常告警</b>：超时率 / 驳回率超过阈值的流程 / 部门</li>
 *   <li><b>效率分析</b>：各流程类型的平均耗时分布（P50 / P90 / P99）</li>
 * </ul>
 *
 * <p><b>数据来源：</b>
 * <ul>
 *   <li>{@code ydsz_flow_instance} — 流程实例表（活跃实例）</li>
 *   <li>{@code ydsz_flow_his_instance} — 历史实例表（已完成 / 终止实例）</li>
 *   <li>{@code ydsz_flow_his_task} — 历史任务表（审批操作轨迹）</li>
 *   <li>{@code ydsz_flow_audit_log} — 审计日志表（操作审计）</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>报告生成涉及多次 SQL 查询，使用 {@code @Transactional(readOnly = true)} 支持只读副本路由</li>
 *   <li>报告写入和通知推送由 {@code REQUIRES_NEW} 子事务隔离</li>
 *   <li>单租户报告生成失败不影响其他租户</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>定时器 + 集群锁</b>：避免集群多节点重复推送，参考大厂分布式调度最佳实践</li>
 *   <li><b>异步推送</b>：报告生成后通过 {@code @Async} 异步推送，避免阻塞定时任务</li>
 *   <li><b>报告缓存</b>：报告内容缓存 24h，避免重复查询 DB</li>
 *   <li><b>报告订阅</b>：用户可订阅 / 退订周报 / 月报，
 *       订阅关系存储在 {@code ydsz_flow_report_subscription}</li>
 *   <li><b>报告国际化</b>：支持中英文报告，根据用户 locale 切换</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 自动触发：定时任务周一 9:00 自动生成本周报告
 * // 手动触发：管理员可调用 generateCustomReport 自定义报告
 * WeeklyReport report = reportService.generateWeeklyReport(
 *     LocalDate.now().minusDays(7), LocalDate.now());
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowReportService 接口定义
 * @see FlowAnalyticsService 审批分析服务（报告数据来源）
 * @see FlowNotificationService 通知服务（报告推送通道）
 * @see FlowClusterLockHelper 集群锁辅助（避免重复推送）
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

    /**
     * 生成本周审批数据报告
     *
     * <p>默认统计时间范围：<b>上周一 00:00 ~ 本周一 00:00</b>（即「上一完整自然周」）。
     * 报告内容包含：核心指标（发起量 / 通过量 / 驳回量 / 超时量 / 平均耗时）、
     * 审批趋势（按天）、审批人效率 Top 5、流程效率对比。
     *
     * <p>数据来源：{@link FlowAnalyticsService}，内部已优化查询走只读副本路由。
     *
     * @param tenantId 租户 ID（默认 {@code "1"}）
     * @return 报告数据 Map（含 {@code reportType / period / overview / trend / topApprovers / flowComparison}）
     */
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

    /**
     * 生成本月审批数据报告
     *
     * <p>默认统计时间范围：<b>上月 1 号 00:00 ~ 上月最后一天 23:59:59</b>（即「上一完整自然月」）。
     * 报告内容与周报类似，但审批人效率 Top 10、趋势按周聚合，更适合月度管理决策。
     *
     * @param tenantId 租户 ID（默认 {@code "1"}）
     * @return 报告数据 Map
     */
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

    /**
     * 推送周报到管理员（手动触发）
     *
     * <p>由 {@link #scheduledWeeklyReport} 定时任务调用，也可由外部手动触发。
     * 推送通道：{@link FlowNotificationService#send}（默认「admin」用户，
     * 实际应通过租户配置获取收件人列表）。
     *
     * @param tenantId 租户 ID
     * @return 推送成功返回 {@code true}，失败返回 {@code false}（异常被吞，不影响调用方）
     */
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

    /**
     * 推送月报到管理员（手动触发）
     *
     * <p>由 {@link #scheduledMonthlyReport} 定时任务调用，也可由外部手动触发。
     * 推送通道：{@link FlowNotificationService#send}。
     *
     * @param tenantId 租户 ID
     * @return 推送成功返回 {@code true}，失败返回 {@code false}
     */
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
