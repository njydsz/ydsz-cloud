package com.njydsz.pmis.workflow.server.service.analytics;

import java.util.Map;

/**
 * P2-4: 审批数据周报/月报服务
 *
 * <p>对标钉钉"审批周报"能力。定时聚合审批数据，生成周报/月报并推送给管理者。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public interface FlowReportService {

    /**
     * 生成周报数据。
     *
     * @param tenantId 租户 ID
     * @return 周报数据 Map
     */
    Map<String, Object> generateWeeklyReport(String tenantId);

    /**
     * 生成月报数据。
     *
     * @param tenantId 租户 ID
     * @return 月报数据 Map
     */
    Map<String, Object> generateMonthlyReport(String tenantId);

    /**
     * 生成并推送周报。
     *
     * @param tenantId 租户 ID
     * @return 是否推送成功
     */
    boolean sendWeeklyReport(String tenantId);

    /**
     * 生成并推送月报。
     *
     * @param tenantId 租户 ID
     * @return 是否推送成功
     */
    boolean sendMonthlyReport(String tenantId);
}
