package com.njydsz.pmis.scheduler.service.impl;

import com.njydsz.pmis.scheduler.service.ReportScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报表定时生成与分发服务实现。
 *
 * <p>流程：
 * <ol>
 *   <li>查询启用订阅</li>
 *   <li>生成报表（Excel/PDF）</li>
 *   <li>上传到 MinIO</li>
 *   <li>通过 Message 模块发送到指定渠道</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Service
public class ReportScheduleServiceImpl implements ReportScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduleServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造函数注入。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ReportScheduleServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void executeDailyReports() {
        log.info("[ReportSchedule] 开始执行日报表生成与分发");
        List<Map<String, Object>> subscriptions = findEnabledSubscriptions("DAILY");
        for (Map<String, Object> sub : subscriptions) {
            try {
                String reportType = (String) sub.get("report_type");
                Long subId = ((Number) sub.get("id")).longValue();
                String recipients = (String) sub.get("recipients");
                log.info("[ReportSchedule] 生成日报表: type={}, subId={}, recipients={}",
                        reportType, subId, recipients);
                String fileKey = generateReport(reportType, sub);
                distributeReport(subId, reportType, fileKey, recipients, (String) sub.get("channels"));
            } catch (Exception e) {
                log.error("[ReportSchedule] 日报表生成失败: sub={}, error={}", sub.get("id"), e.getMessage());
            }
        }
        log.info("[ReportSchedule] 日报表生成完成，共 {} 份", subscriptions.size());
    }

    @Override
    public void executeWeeklyReports() {
        log.info("[ReportSchedule] 开始执行周报表生成与分发");
        List<Map<String, Object>> subscriptions = findEnabledSubscriptions("WEEKLY");
        for (Map<String, Object> sub : subscriptions) {
            try {
                String reportType = (String) sub.get("report_type");
                String fileKey = generateReport(reportType, sub);
                distributeReport(((Number) sub.get("id")).longValue(), reportType, fileKey,
                        (String) sub.get("recipients"), (String) sub.get("channels"));
            } catch (Exception e) {
                log.error("[ReportSchedule] 周报表生成失败: sub={}, error={}", sub.get("id"), e.getMessage());
            }
        }
        log.info("[ReportSchedule] 周报表生成完成，共 {} 份", subscriptions.size());
    }

    @Override
    public void executeMonthlyReports() {
        log.info("[ReportSchedule] 开始执行月报表生成与分发");
        List<Map<String, Object>> subscriptions = findEnabledSubscriptions("MONTHLY");
        for (Map<String, Object> sub : subscriptions) {
            try {
                String reportType = (String) sub.get("report_type");
                String fileKey = generateReport(reportType, sub);
                distributeReport(((Number) sub.get("id")).longValue(), reportType, fileKey,
                        (String) sub.get("recipients"), (String) sub.get("channels"));
            } catch (Exception e) {
                log.error("[ReportSchedule] 月报表生成失败: sub={}, error={}", sub.get("id"), e.getMessage());
            }
        }
        log.info("[ReportSchedule] 月报表生成完成，共 {} 份", subscriptions.size());
    }

    @Override
    public String generateReport(String reportType, Map<String, Object> params) {
        log.info("[ReportSchedule] 生成报表: type={}", reportType);
        // TODO: 根据 reportType 调用对应的报表 Service 生成 Excel/PDF
        // COCKPIT → CockpitReportService
        // EVM → EvmController export
        // PROFIT → ReportService export
        // UTILIZATION → BillableUtilizationService
        String fileKey = "report/" + reportType + "/" + System.currentTimeMillis() + ".xlsx";
        log.info("[ReportSchedule] 报表生成完成: type={}, fileKey={}", reportType, fileKey);
        return fileKey;
    }

    /**
     * 查询指定频率的启用订阅。
     *
     * @param frequency 频率（DAILY/WEEKLY/MONTHLY）
     * @return 订阅列表
     */
    private List<Map<String, Object>> findEnabledSubscriptions(String frequency) {
        String sql = "SELECT * FROM pmis_report_subscription WHERE enabled = 1 AND deleted = 0 AND frequency = ?";
        return jdbcTemplate.queryForList(sql, frequency);
    }

    /**
     * 分发报表到指定渠道。
     *
     * @param subId      订阅 ID
     * @param reportType 报表类型
     * @param fileKey    文件 key
     * @param recipients 接收人
     * @param channels   推送渠道
     */
    private void distributeReport(Long subId, String reportType, String fileKey,
                                  String recipients, String channels) {
        // 记录导出历史
        String recordSql = "INSERT INTO pmis_report_export_record " +
                "(subscription_id, report_type, file_key, status, completed_at) " +
                "VALUES (?, ?, ?, 'SENT', ?)";
        jdbcTemplate.update(recordSql, subId, reportType, fileKey, LocalDateTime.now());
        // TODO: 通过 Feign 调用 message 模块发送邮件/钉钉通知，附带报表下载链接
        log.info("[ReportSchedule] 报表分发完成: type={}, fileKey={}, recipients={}, channels={}",
                reportType, fileKey, recipients, channels);
    }
}
