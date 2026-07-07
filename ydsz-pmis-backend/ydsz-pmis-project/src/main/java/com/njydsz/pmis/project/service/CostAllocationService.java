package com.njydsz.pmis.project.service;

import com.njydsz.pmis.project.entity.CostAllocationDO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 成本归集服务
 *
 * <p>按项目/期间/成本类型归集人力/采购/费用/外包/分摊成本，用于利润核算与对账。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface CostAllocationService {

    /**
     * 同步一条工时到成本归集（人力成本）
     */
    Long syncFromTimeEntry(String timeEntryId, String initiationId, String employeeId,
                            String employeeName, String levelCode,
                            String period, BigDecimal amount, boolean billable);

    /**
     * 同步采购成本
     */
    Long syncFromPurchase(String purchaseId, String initiationId, String period,
                           BigDecimal amount, boolean billable);

    /**
     * 同步费用成本
     */
    Long syncFromExpense(String expenseId, String initiationId, String period,
                          BigDecimal amount, boolean billable);

    /**
     * 按成本类型月度汇总
     */
    List<Map<String, Object>> monthlySummary(String initiationId);

    /**
     * 按类型/来源汇总
     */
    List<Map<String, Object>> sumByType(String initiationId, String period);

    /**
     * 查询项目某月成本归集明细
     */
    List<CostAllocationDO> listByInitiationAndPeriod(String initiationId, String period);

    /**
     * 标记已分摊
     */
    void markAllocated(List<String> ids);
}
