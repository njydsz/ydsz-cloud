package com.njydsz.pmis.project.service.report;

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
     * @return 利润报表数据
     */
    Map<String, Object> projectProfitReport(String initiationId, String period);

    /**
     * 项目成本归集明细表
     *
     * @param initiationId 项目 ID
     * @param period       期间（YYYY-MM，可空，空表示累计）
     * @return 成本明细报表数据
     */
    Map<String, Object> costDetailReport(String initiationId, String period);

    /**
     * 项目回款台账
     *
     * @param initiationId 项目 ID
     * @return 回款台账数据
     */
    Map<String, Object> paymentLedgerReport(String initiationId);

    /**
     * 项目全生命周期台账
     *
     * @param initiationId 项目 ID
     * @return 全生命周期台账数据
     */
    Map<String, Object> projectLifecycleReport(String initiationId);

    /**
     * 跨项目利润表（汇总）
     *
     * @return 所有项目利润汇总列表
     */
    List<Map<String, Object>> profitSummaryAll();

    /**
     * 项目利润排行榜（P2-1 体验增强）
     *
     * <p>基于 ProfitSnapshot 表，按每个项目最新一次快照聚合，
     * 排序维度支持 {@code grossMargin}（毛利率）/ {@code grossProfit}（毛利金额）/
     * {@code contractAmount}（合同金额）。
     *
     * @param top      返回 Top N（默认 10）
     * @param sortBy   排序维度，默认 grossMargin
     * @param period   期间过滤（YYYY-MM，可空，空表示不按期间过滤）
     * @return 利润排行榜列表
     */
    List<Map<String, Object>> profitRank(int top, String sortBy, String period);
}
