package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.feign.InitiationServiceClient;
import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
import com.njydsz.pmis.execution.mapper.ExpenseMapper;
import com.njydsz.pmis.execution.mapper.PurchaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 预算强管控引擎
 * <p>
 * 业务规则（PRD 3.2 节 预算强管控）：
 * <ol>
 *   <li>采购/费用新增前必须校验「本单 + 项目已发生」≤ 立项预算</li>
 *   <li>当项目服务不可用时自动降级（跳过校验 + 记录告警）</li>
 *   <li>当立项未设置预算(budgetAmount=null/0)时跳过校验</li>
 *   <li>提供预警：累计使用达 80% 触发黄色告警，95% 触发红色告警</li>
 * </ol>
 * </p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetGuard {

    private final InitiationServiceClient initiationClient;
    private final PurchaseMapper purchaseMapper;
    private final ExpenseMapper expenseMapper;
    private final CostAllocationMapper costAllocationMapper;

    /** 黄色告警阈值 */
    public static final BigDecimal YELLOW_RATIO = new BigDecimal("0.80");
    /** 红色告警阈值 */
    public static final BigDecimal RED_RATIO = new BigDecimal("0.95");

    /**
     * 强管控校验：本次新增后(已发生 + 本次) 是否超出预算
     *
     * @param initiationId 项目立项 ID
     * @param delta        本次新增金额（采购/费用）
     * @param bizType      业务类型: PURCHASE / EXPENSE
     * @throws BizException 当超出预算时抛出
     */
    public void check(Long initiationId, BigDecimal delta, String bizType) {
        if (initiationId == null || delta == null || delta.signum() <= 0) {
            return; // 未关联项目或金额为 0/负，无需校验
        }
        Map<String, Object> snap = safeBudgetSnapshot(initiationId);
        if (snap == null) {
            log.warn("[BudgetGuard] 项目 {} 预算快照不可用，{} 本次 {} 元已自动放行", initiationId, bizType, delta);
            return;
        }
        Object bj = snap.get("budgetAmount");
        if (bj == null) return;
        BigDecimal budget = toBigDecimal(bj);
        if (budget == null || budget.signum() <= 0) {
            log.debug("[BudgetGuard] 项目 {} 未设置预算，跳过强管控", initiationId);
            return;
        }

        BigDecimal purchaseUsed = nz(purchaseMapper.sumByInitiation(initiationId));
        BigDecimal expenseUsed = nz(expenseMapper.sumByInitiation(initiationId));
        BigDecimal allocatedUsed = nz(costAllocationMapper.sumByInitiation(initiationId));
        // 已发生 = 采购已发生 + 费用已发生 + 已归集成本
        BigDecimal used = purchaseUsed.add(expenseUsed).add(allocatedUsed);
        BigDecimal afterUsed = used.add(delta);
        BigDecimal ratio = afterUsed.divide(budget, 4, RoundingMode.HALF_UP);

        log.info("[BudgetGuard] 项目 {} {} 本次 {} 元 | 预算 {} | 已发生 {} (采购 {} + 费用 {} + 已归集 {}) | 累计 {}({}%)",
                initiationId, bizType, delta, budget, used, purchaseUsed, expenseUsed, allocatedUsed, afterUsed, ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));

        if (afterUsed.compareTo(budget) > 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    String.format("[预算强管控] 项目[%s] 累计 %s 元已超出预算 %s 元（采购 %s + 费用 %s + 已归集 %s + 本次 %s）",
                            snap.get("projectCode"), afterUsed.toPlainString(), budget.toPlainString(),
                            purchaseUsed.toPlainString(), expenseUsed.toPlainString(),
                            allocatedUsed.toPlainString(), delta.toPlainString()));
        }
        // 黄色 / 红色 预警
        if (ratio.compareTo(RED_RATIO) >= 0) {
            log.warn("[BudgetGuard-RED] 项目 {} {} 累计使用率 {}% 已触及红色告警阈值(95%)", initiationId, bizType, ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        } else if (ratio.compareTo(YELLOW_RATIO) >= 0) {
            log.warn("[BudgetGuard-YELLOW] 项目 {} {} 累计使用率 {}% 已触及黄色告警阈值(80%)", initiationId, bizType, ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        }
    }

    /**
     * 查询预算占用率（供报表使用）
     *
     * @return {used, budget, ratio, alertLevel}；alertLevel: NORMAL/YELLOW/RED
     */
    public Map<String, Object> occupancy(Long initiationId) {
        Map<String, Object> snap = safeBudgetSnapshot(initiationId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (snap == null) {
            result.put("used", BigDecimal.ZERO);
            result.put("budget", BigDecimal.ZERO);
            result.put("ratio", BigDecimal.ZERO);
            result.put("alertLevel", "UNKNOWN");
            return result;
        }
        BigDecimal budget = toBigDecimal(snap.get("budgetAmount"));
        BigDecimal used = nz(purchaseMapper.sumByInitiation(initiationId))
                .add(nz(expenseMapper.sumByInitiation(initiationId)))
                .add(nz(costAllocationMapper.sumByInitiation(initiationId)));
        BigDecimal ratio = (budget != null && budget.signum() > 0)
                ? used.divide(budget, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        String alert = "NORMAL";
        if (ratio.compareTo(RED_RATIO) >= 0) alert = "RED";
        else if (ratio.compareTo(YELLOW_RATIO) >= 0) alert = "YELLOW";
        result.put("initiationId", initiationId);
        result.put("projectCode", snap.get("projectCode"));
        result.put("projectName", snap.get("projectName"));
        result.put("used", used);
        result.put("budget", budget);
        result.put("ratio", ratio);
        result.put("alertLevel", alert);
        return result;
    }

    private Map<String, Object> safeBudgetSnapshot(Long initiationId) {
        try {
            R<Map<String, Object>> r = initiationClient.budgetSnapshot(initiationId);
            if (r == null || !r.isSuccess() || r.getData() == null) {
                log.warn("[BudgetGuard] budgetSnapshot 返回空: code={} msg={}",
                        r == null ? "null" : r.getCode(), r == null ? "?" : r.getMessage());
                return null;
            }
            return r.getData();
        } catch (Exception e) {
            log.warn("[BudgetGuard] budgetSnapshot 调用异常，已降级: {}", e.getMessage());
            return null;
        }
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return null; }
    }
}
