package com.njydsz.pmis.execution.service;

import java.util.List;
import java.util.Map;

/**
 * 基础报表服务
 *
 * <p>提供 4 类基础报表：
 * <ul>
 *   <li>项目利润表：收入 / 成本 / 毛利 / 毛利率</li>
 *   <li>项目成本归集明细表：人力 / 采购 / 费用 / 分摊 四维拆解</li>
 *   <li>项目回款台账：回款记录 + 核销情况</li>
 *   <li>项目全生命周期台账：商机 → 立项 → 合同 → 变更 → 结项</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ReportService {

    /**
     * 项目利润表
     *
     * @param initiationId 项目 ID
     * @param period       期间（YYYY-MM，可空，空表示累计）
     */
    Map<String, Object> projectProfitReport(Long initiationId, String period);

    /**
     * 项目成本归集明细表
     */
    Map<String, Object> costDetailReport(Long initiationId, String period);

    /**
     * 项目回款台账
     */
    Map<String, Object> paymentLedgerReport(Long initiationId);

    /**
     * 项目全生命周期台账
     */
    Map<String, Object> projectLifecycleReport(Long initiationId);

    /**
     * 跨项目利润表（汇总）
     */
    List<Map<String, Object>> profitSummaryAll();
}
