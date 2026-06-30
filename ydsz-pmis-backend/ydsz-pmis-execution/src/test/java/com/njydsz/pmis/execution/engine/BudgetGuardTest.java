package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.feign.InitiationServiceClient;
import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
import com.njydsz.pmis.execution.mapper.ExpenseMapper;
import com.njydsz.pmis.execution.mapper.PurchaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BudgetGuard 预算强管控引擎单元测试
 */
@DisplayName("BudgetGuard 预算强管控测试")
class BudgetGuardTest {

    private InitiationServiceClient initiationClient;
    private PurchaseMapper purchaseMapper;
    private ExpenseMapper expenseMapper;
    private CostAllocationMapper costAllocationMapper;
    private BudgetGuard guard;

    @BeforeEach
    void setUp() {
        initiationClient = mock(InitiationServiceClient.class);
        purchaseMapper = mock(PurchaseMapper.class);
        expenseMapper = mock(ExpenseMapper.class);
        costAllocationMapper = mock(CostAllocationMapper.class);
        guard = new BudgetGuard(initiationClient, purchaseMapper, expenseMapper, costAllocationMapper);

        // 默认三个 mapper 都返回 0
        when(purchaseMapper.sumByInitiation(anyLong())).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.sumByInitiation(anyLong())).thenReturn(BigDecimal.ZERO);
        when(costAllocationMapper.sumByInitiation(anyLong())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("check - null 立项/金额 直接放行")
    void checkNullInputs() {
        guard.check(null, new BigDecimal("100"), "PURCHASE");
        guard.check(1L, null, "PURCHASE");
        guard.check(1L, BigDecimal.ZERO, "PURCHASE");
        guard.check(1L, new BigDecimal("-1"), "PURCHASE");
        // 不会调用 mapper
        org.mockito.Mockito.verify(purchaseMapper, org.mockito.Mockito.never()).sumByInitiation(anyLong());
    }

    @Test
    @DisplayName("check - 项目服务降级 自动放行")
    void checkDegraded() {
        when(initiationClient.budgetSnapshot(1L)).thenReturn(R.failed(503, "降级"));
        guard.check(1L, new BigDecimal("100"), "PURCHASE");
        // 降级不抛异常
    }

    @Test
    @DisplayName("check - 预算为 0/未设置 跳过校验")
    void checkZeroBudget() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("projectCode", "P-1");
        snap.put("budgetAmount", null);
        when(initiationClient.budgetSnapshot(1L)).thenReturn(R.ok(snap));
        guard.check(1L, new BigDecimal("9999999"), "PURCHASE");
    }

    @Test
    @DisplayName("check - 在预算内放行")
    void checkWithinBudget() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("projectCode", "P-1");
        snap.put("budgetAmount", new BigDecimal("10000"));
        when(initiationClient.budgetSnapshot(1L)).thenReturn(R.ok(snap));
        when(purchaseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("3000"));
        when(expenseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("2000"));
        when(costAllocationMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("0"));
        // 已发生 5000 + 本次 4000 = 9000 < 10000 → 放行
        guard.check(1L, new BigDecimal("4000"), "PURCHASE");
    }

    @Test
    @DisplayName("check - 超出预算抛 BizException")
    void checkExceed() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("projectCode", "P-1");
        snap.put("budgetAmount", new BigDecimal("10000"));
        when(initiationClient.budgetSnapshot(1L)).thenReturn(R.ok(snap));
        when(purchaseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("8000"));
        when(expenseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("0"));
        // 8000 + 3000 = 11000 > 10000 → 抛异常
        assertThatThrownBy(() -> guard.check(1L, new BigDecimal("3000"), "PURCHASE"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("预算强管控");
    }

    @Test
    @DisplayName("check - 刚好等于预算 放行")
    void checkEqualBudget() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("projectCode", "P-1");
        snap.put("budgetAmount", new BigDecimal("10000"));
        when(initiationClient.budgetSnapshot(1L)).thenReturn(R.ok(snap));
        when(purchaseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("7000"));
        when(expenseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("0"));
        // 7000 + 3000 = 10000  == 10000 → 放行
        guard.check(1L, new BigDecimal("3000"), "EXPENSE");
    }

    @Test
    @DisplayName("occupancy - 红色告警(>=95%)")
    void occupancyRed() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("projectCode", "P-RED");
        snap.put("projectName", "红色项目");
        snap.put("budgetAmount", new BigDecimal("10000"));
        when(initiationClient.budgetSnapshot(1L)).thenReturn(R.ok(snap));
        when(purchaseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("9000"));
        when(expenseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("0"));
        when(costAllocationMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("500"));
        // 9500 / 10000 = 95% → RED
        Map<String, Object> r = guard.occupancy(1L);
        assertThat(r.get("alertLevel")).isEqualTo("RED");
        assertThat(r.get("projectCode")).isEqualTo("P-RED");
    }

    @Test
    @DisplayName("occupancy - 黄色告警(>=80% & <95%)")
    void occupancyYellow() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("projectCode", "P-Y");
        snap.put("projectName", "黄色项目");
        snap.put("budgetAmount", new BigDecimal("10000"));
        when(initiationClient.budgetSnapshot(1L)).thenReturn(R.ok(snap));
        when(purchaseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("8000"));
        when(expenseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("0"));
        when(costAllocationMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("0"));
        // 8000 / 10000 = 80% → YELLOW
        Map<String, Object> r = guard.occupancy(1L);
        assertThat(r.get("alertLevel")).isEqualTo("YELLOW");
    }

    @Test
    @DisplayName("occupancy - 正常(<80%)")
    void occupancyNormal() {
        Map<String, Object> snap = new HashMap<>();
        snap.put("projectCode", "P-N");
        snap.put("budgetAmount", new BigDecimal("10000"));
        when(initiationClient.budgetSnapshot(1L)).thenReturn(R.ok(snap));
        when(purchaseMapper.sumByInitiation(1L)).thenReturn(new BigDecimal("5000"));
        // 5000/10000 = 50% → NORMAL
        Map<String, Object> r = guard.occupancy(1L);
        assertThat(r.get("alertLevel")).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("occupancy - 降级时返回 UNKNOWN")
    void occupancyDegraded() {
        when(initiationClient.budgetSnapshot(1L)).thenReturn(R.failed(503, "降级"));
        Map<String, Object> r = guard.occupancy(1L);
        assertThat(r.get("alertLevel")).isEqualTo("UNKNOWN");
    }
}
