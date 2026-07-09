package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.feign.InitiationServiceClient;
import com.njydsz.pmis.project.mapper.execution.CostAllocationMapper;
import com.njydsz.pmis.project.mapper.finance.ExpenseMapper;
import com.njydsz.pmis.project.mapper.execution.PurchaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
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
    /**
     * Spring 事件发布器; null-safe(单元测试场景下未注入时直接跳过)
     */
    private final ApplicationEventPublisher eventPublisher;

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
    public void check(String initiationId, BigDecimal delta, String bizType) {
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
        // 黄色 / 红色 预警 -> 发布事件 (通知中心 / 预警中心 / RocketMQ 推送等监听器订阅)
        if (ratio.compareTo(RED_RATIO) >= 0) {
            log.warn("[BudgetGuard-RED] 项目 {} {} 累计使用率 {}% 已触及红色告警阈值(95%)",
                    initiationId, bizType, percent(ratio));
            publishAlert(snap, initiationId, bizType, delta, afterUsed, budget, ratio,
                    BudgetAlertEvent.Level.RED);
        } else if (ratio.compareTo(YELLOW_RATIO) >= 0) {
            log.warn("[BudgetGuard-YELLOW] 项目 {} {} 累计使用率 {}% 已触及黄色告警阈值(80%)",
                    initiationId, bizType, percent(ratio));
            publishAlert(snap, initiationId, bizType, delta, afterUsed, budget, ratio,
                    BudgetAlertEvent.Level.YELLOW);
        }
    }

    /**
     * 查询预算占用率（供报表使用）
     *
     * @param initiationId 项目立项 ID
     * @return {used, budget, ratio, alertLevel}；alertLevel: NORMAL/YELLOW/RED
     */
    public Map<String, Object> occupancy(String initiationId) {
        Map<String, Object> snap = safeBudgetSnapshot(initiationId);
        Map<String, Object> R = new LinkedHashMap<>();
        if (snap == null) {
            R.put("used", BigDecimal.ZERO);
            R.put("budget", BigDecimal.ZERO);
            R.put("ratio", BigDecimal.ZERO);
            R.put("alertLevel", "UNKNOWN");
            return R;
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
        R.put("initiationId", initiationId);
        R.put("projectCode", snap.get("projectCode"));
        R.put("projectName", snap.get("projectName"));
        R.put("used", used);
        R.put("budget", budget);
        R.put("ratio", ratio);
        R.put("alertLevel", alert);
        return R;
    }

    /**
     * 安全获取预算快照（Feign + try-catch 降级）
     *
     * @param initiationId 项目立项 ID
     * @return 预算快照；服务不可用或返回空时返回 null
     */
    private Map<String, Object> safeBudgetSnapshot(String initiationId) {
        try {
            Result<Map<String, Object>> r = initiationClient.budgetSnapshot(initiationId);
            if (r == null || !r.isSuccess() || r.getData() == null) {
                log.warn("[BudgetGuard] budgetSnapshot 返回空: code={} msg={}",
                        r == null ? "null" : r.getCode(), r == null ? "?" : r.getMessage());
                return null;
            }
            return r.getData();
        } catch (Exception e) {
            log.error("[BudgetGuard] budgetSnapshot 调用异常，已降级: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 发布预算告警事件
     *
     * @param snap       预算快照
     * @param initiationId 项目立项 ID
     * @param bizType    业务类型
     * @param delta      本次新增金额
     * @param usedAfter  累计使用金额
     * @param budget     预算总额
     * @param ratio      占用率
     * @param level      告警级别
     */
    private void publishAlert(Map<String, Object> snap, String initiationId, String bizType,
                              BigDecimal delta, BigDecimal usedAfter, BigDecimal budget,
                              BigDecimal ratio, BudgetAlertEvent.Level level) {
        if (eventPublisher == null) {
            // 单测或非 Spring 容器场景, 仅记录日志
            return;
        }
        try {
            BudgetAlertEvent event = BudgetAlertEvent.builder()
                    .initiationId(initiationId)
                    .projectCode(snap == null ? null : str(snap.get("projectCode")))
                    .projectName(snap == null ? null : str(snap.get("projectName")))
                    .bizType(bizType)
                    .delta(delta)
                    .usedAfter(usedAfter)
                    .budget(budget)
                    .ratio(ratio)
                    .level(level)
                    .timestamp(System.currentTimeMillis())
                    .build();
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            // 事件发布失败不影响主业务流
            log.warn("[BudgetGuard] 预算告警事件发布失败: {}", e.getMessage());
        }
    }

    /**
     * 对象转字符串
     *
     * @param o 原始对象
     * @return 字符串；null 返回 null
     */
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    /**
     * 占用率转百分比
     *
     * @param ratio 占用率
     * @return 百分比数值
     */
    private static BigDecimal percent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 空值转零
     *
     * @param v 原始值
     * @return 非空原值；null 返回 ZERO
     */
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

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
        try { return new BigDecimal(o.toString()); } catch (NumberFormatException e) { log.warn("[BudgetGuard] 对象转BigDecimal失败: value={}", o, e); return null; }
    }
}
