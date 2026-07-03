package com.njydsz.pmis.cronjob.service;

import java.util.Map;

/**
 * 报表定时生成与分发服务。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ReportScheduleService {

    /**
     * 执行日报表生成与分发。
     */
    void executeDailyReports();

    /**
     * 执行周报表生成与分发。
     */
    void executeWeeklyReports();

    /**
     * 执行月报表生成与分发。
     */
    void executeMonthlyReports();

    /**
     * 手动触发指定报表生成。
     *
     * @param reportType 报表类型
     * @param params     生成参数
     * @return 文件 key
     */
    String generateReport(String reportType, Map<String, Object> params);

    /**
     * 分发报表：落库 pmis_report_export_record 并发送邮件通知。
     *
     * @param subId      订阅 ID
     * @param reportType 报表类型
     * @param fileKey    MinIO 对象 key
     * @param recipients 接收人
     * @param channels   分发通道
     */
    void distributeReport(Long subId, String reportType, String fileKey, String recipients, String channels);
}
