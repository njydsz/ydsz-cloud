package com.njydsz.pmis.project.literule;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.feign.InitiationServiceClient;
import com.njydsz.pmis.project.mapper.CostAllocationMapper;
import com.njydsz.pmis.project.mapper.ExpenseMapper;
import com.njydsz.pmis.project.mapper.PurchaseMapper;
import com.njydsz.pmis.literule.spi.BudgetSnapshotProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 预算快照提供者实现（execution 模块）
 *
 * <p>实现 literule 模块的 {@link BudgetSnapshotProvider} SPI 接口，
 * 通过 InitiationServiceClient 获取立项预算，通过 Mapper 汇总已发生成本。
 *
 * <p>说明：
 * <ul>
 *   <li>接口中 projectId 为 String 类型，内部转换为 Long initiationId 使用</li>
 *   <li>{@link #getPendingAmount} 简化返回 ZERO，实际申请金额由调用方传入</li>
 *   <li>{@link #getBudgetSnapshots} 暂返回空列表，待接入活跃项目列表查询</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetSnapshotProviderImpl implements BudgetSnapshotProvider {

    private final InitiationServiceClient initiationClient;
    private final PurchaseMapper purchaseMapper;
    private final ExpenseMapper expenseMapper;
    private final CostAllocationMapper costAllocationMapper;

    /**
     * 获取项目预算总额
     *
     * <p>通过 InitiationServiceClient 查询立项预算快照，提取 budgetAmount 字段。
     *
     * @param projectId 项目 ID（对应立项 initiationId 的字符串形式）
     * @return 预算总额；不存在或服务不可用返回 {@link BigDecimal#ZERO}
     */
    @Override
    public BigDecimal getTotalBudget(String projectId) {
        Long initiationId = parseInitiationId(projectId);
        if (initiationId == null) {
            return BigDecimal.ZERO;
        }
        Map<String, Object> snap = safeBudgetSnapshot(initiationId);
        if (snap == null) {
            log.warn("[BudgetSnapshotProvider] 项目 {} 预算快照不可用，返回 ZERO", projectId);
            return BigDecimal.ZERO;
        }
        BigDecimal budget = toBigDecimal(snap.get("budgetAmount"));
        return budget == null ? BigDecimal.ZERO : budget;
    }

    /**
     * 获取项目已发生成本（采购 + 费用 + 成本分摊）
     *
     * <p>汇总三个 Mapper 的 sumByInitiation 结果：
     * <ul>
     *   <li>{@link PurchaseMapper#sumByInitiation(Long)} 采购已发生金额</li>
     *   <li>{@link ExpenseMapper#sumByInitiation(Long)} 费用已发生金额</li>
     *   <li>{@link CostAllocationMapper#sumByInitiation(Long)} 已归集成本金额</li>
     * </ul>
     *
     * @param projectId 项目 ID（对应立项 initiationId 的字符串形式）
     * @return 已发生成本；查询失败返回 {@link BigDecimal#ZERO}
     */
    @Override
    public BigDecimal getIncurredCost(String projectId) {
        Long initiationId = parseInitiationId(projectId);
        if (initiationId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal purchaseUsed = nz(purchaseMapper.sumByInitiation(initiationId));
        BigDecimal expenseUsed = nz(expenseMapper.sumByInitiation(initiationId));
        BigDecimal allocatedUsed = nz(costAllocationMapper.sumByInitiation(initiationId));
        BigDecimal incurred = purchaseUsed.add(expenseUsed).add(allocatedUsed);
        log.debug("[BudgetSnapshotProvider] 项目 {} 已发生成本: 采购 {} + 费用 {} + 已归集 {} = {}",
                projectId, purchaseUsed, expenseUsed, allocatedUsed, incurred);
        return incurred;
    }

    /**
     * 获取项目本次申请金额
     *
     * <p>简化实现：始终返回 {@link BigDecimal#ZERO}。
     * 实际申请金额由调用方通过 {@code getUsageRatio(projectId, pendingAmount)} 的
     * pendingAmount 参数传入，无需通过此方法查询。
     *
     * @param projectId 项目 ID
     * @param requestId 申请单 ID
     * @return 申请金额（简化返回 ZERO）
     */
    @Override
    public BigDecimal getPendingAmount(String projectId, String requestId) {
        // TODO 申请金额由调用方传入，此处简化返回 ZERO
        return BigDecimal.ZERO;
    }

    /**
     * 获取全部预算预警相关项目的快照
     *
     * <p>当前为简化实现，返回空列表。
     * 完整实现需通过 InitiationServiceClient 批量查询活跃项目列表，
     * 或查询本地表获取所有 initiationId 后逐个汇总。
     *
     * @return 项目预算快照列表（当前返回空列表）
     */
    @Override
    public List<BudgetSnapshot> getBudgetSnapshots() {
        // TODO 待接入活跃项目列表查询（需 InitiationServiceClient 新增批量接口或查询本地表）
        log.debug("[BudgetSnapshotProvider] getBudgetSnapshots 暂未实现，返回空列表");
        return Collections.emptyList();
    }

    // -------------------- 内部工具方法 --------------------

    /**
     * 将 projectId（String）解析为 initiationId（Long）
     *
     * @param projectId 项目 ID 字符串
     * @return 立项 ID；解析失败返回 null
     */
    private Long parseInitiationId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(projectId.trim());
        } catch (NumberFormatException e) {
            log.warn("[BudgetSnapshotProvider] projectId={} 无法解析为 Long", projectId);
            return null;
        }
    }

    /**
     * 安全获取预算快照（Feign + try-catch 降级）
     *
     * <p>复用 {@code BudgetGuard.safeBudgetSnapshot} 的降级策略：
     * 当项目服务不可用时返回 null，调用方据此跳过预算校验。
     *
     * @param initiationId 项目立项 ID
     * @return 预算快照 Map；服务不可用或返回空时返回 null
     */
    private Map<String, Object> safeBudgetSnapshot(Long initiationId) {
        try {
            Result<Map<String, Object>> r = initiationClient.budgetSnapshot(initiationId);
            if (r == null || !r.isSuccess() || r.getData() == null) {
                log.warn("[BudgetSnapshotProvider] budgetSnapshot 返回空: code={} msg={}",
                        r == null ? "null" : r.getCode(), r == null ? "?" : r.getMessage());
                return null;
            }
            return r.getData();
        } catch (Exception e) {
            log.warn("[BudgetSnapshotProvider] budgetSnapshot 调用异常，已降级: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 空值转零
     *
     * @param v 原始值
     * @return 非空原值；null 返回 {@link BigDecimal#ZERO}
     */
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 对象转 BigDecimal
     *
     * @param o 原始对象
     * @return BigDecimal 值；无法转换返回 null
     */
    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
