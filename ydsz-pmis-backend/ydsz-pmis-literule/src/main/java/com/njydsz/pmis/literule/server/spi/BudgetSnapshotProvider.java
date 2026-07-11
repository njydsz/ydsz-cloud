package com.njydsz.pmis.literule.server.spi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 预算快照提供者接口（SPI）
 *
 * <p>由消费方（如 execution 模块）实现，提供预算管控所需的数据快照。
 * literule 模块通过此接口反转依赖，无需直接依赖 Feign/Mapper。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface BudgetSnapshotProvider {

    /**
     * 获取项目预算总额
     *
     * @param projectId 项目 ID
     * @return 预算总额；不存在返回 BigDecimal.ZERO
     */
    BigDecimal getTotalBudget(String projectId);

    /**
     * 获取项目已发生成本（采购+费用+分摊）
     *
     * @param projectId 项目 ID
     * @return 已发生成本
     */
    BigDecimal getIncurredCost(String projectId);

    /**
     * 获取项目本次申请金额
     *
     * @param projectId 项目 ID
     * @param requestId 申请单 ID
     * @return 申请金额
     */
    BigDecimal getPendingAmount(String projectId, String requestId);

    /**
     * 获取预算使用率（已发生+本次申请）/ 预算总额
     *
     * @param projectId 项目 ID
     * @param pendingAmount 本次申请金额
     * @return 使用率（0~1+）；预算为 0 返回 1.0（100%超支）
     */
    default double getUsageRatio(String projectId, BigDecimal pendingAmount) {
        BigDecimal budget = getTotalBudget(projectId);
        if (budget == null || budget.compareTo(BigDecimal.ZERO) == 0) return 1.0;
        BigDecimal total = getIncurredCost(projectId).add(pendingAmount == null ? BigDecimal.ZERO : pendingAmount);
        return total.divide(budget, 4, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 获取全部预算预警相关项目的快照
     *
     * @return 项目预算快照列表
     */
    List<BudgetSnapshot> getBudgetSnapshots();

    /**
     * 预算快照 DTO
     *
     * @author ydsz-pmis-team
     */
    record BudgetSnapshot(
            String projectId,
            String projectName,
            BigDecimal totalBudget,
            BigDecimal incurredCost,
            double usageRatio
    ) {}
}
