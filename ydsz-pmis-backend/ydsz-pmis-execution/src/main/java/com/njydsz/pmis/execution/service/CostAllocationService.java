package com.njydsz.pmis.execution.service;

import com.njydsz.pmis.execution.entity.CostAllocationDO;
import com.njydsz.pmis.execution.enums.CostType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 成本归集服务
 */
public interface CostAllocationService {

    /**
     * 同步一条工时到成本归集（人力成本）
     */
    Long syncFromTimeEntry(Long timeEntryId, Long initiationId, Long employeeId,
                            String employeeName, String levelCode,
                            String period, BigDecimal amount, boolean billable);

    /**
     * 同步采购成本
     */
    Long syncFromPurchase(Long purchaseId, Long initiationId, String period,
                           BigDecimal amount, boolean billable);

    /**
     * 同步费用成本
     */
    Long syncFromExpense(Long expenseId, Long initiationId, String period,
                          BigDecimal amount, boolean billable);

    /**
     * 按成本类型月度汇总
     */
    List<Map<String, Object>> monthlySummary(Long initiationId);

    /**
     * 按类型/来源汇总
     */
    List<Map<String, Object>> sumByType(Long initiationId, String period);

    /**
     * 查询项目某月成本归集明细
     */
    List<CostAllocationDO> listByInitiationAndPeriod(Long initiationId, String period);

    /**
     * 标记已分摊
     */
    void markAllocated(List<Long> ids);
}
