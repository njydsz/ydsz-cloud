package com.remisoft.cronjob.server.service.job;

import java.util.Map;

/**
 * 报表定时生成与分发 Service
 *
 * <p>负责日报/周报/月报的定时生成与多通道分发(邮件/站内通知/IM 机器人),
 * 是"项目运营"场景下定时产出业务报表的核心入口。报表文件落 MinIO,元数据写
 * {@code remi_export_record} 表,接收人通过邮件/IM 收到下载链接。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>定时执行</b>：{@link #executeDailyReports} / {@link #executeWeeklyReports} / {@link #executeMonthlyReports}
 *       — 由 Quartz 调度器在固定时间点触发</li>
 *   <li><b>手动生成</b>：{@link #generateReport} — 管理后台手动触发</li>
 *   <li><b>分发</b>：{@link #distributeReport} — 落库 + 通知接收人</li>
 * </ul>
 *
 * <p><b>报表订阅：</b>用户可订阅关心的报表类型,系统按订阅周期定时推送;
 * 订阅关系存储在 {@code remi_report_subscription} 表。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.cronjob.domain.entity.job.ReportSubscription 报表订阅实体
 * @see com.remisoft.system.domain.entity.config.ExportRecord 导出记录实体(落库)
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
     * 分发报表：落库 remi_export_record（source='SUBSCRIPTION'）并发送邮件通知。
     *
     * @param subId      订阅 ID
     * @param reportType 报表类型
     * @param fileKey    MinIO 对象 key
     * @param recipients 接收人
     * @param channels   分发通道
     */
    void distributeReport(Long subId, String reportType, String fileKey, String recipients, String channels);
}
