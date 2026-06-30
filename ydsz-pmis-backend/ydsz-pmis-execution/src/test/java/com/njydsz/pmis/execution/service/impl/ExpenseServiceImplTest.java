package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.assembler.NameAssembler;
import com.njydsz.pmis.execution.dto.ApprovalDTO;
import com.njydsz.pmis.execution.dto.ExpenseCreateDTO;
import com.njydsz.pmis.execution.engine.BudgetGuard;
import com.njydsz.pmis.execution.entity.ExpenseDO;
import com.njydsz.pmis.execution.mapper.ExpenseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ExpenseServiceImpl 费用服务测试")
class ExpenseServiceImplTest {

    private ExpenseMapper mapper;
    private NameAssembler nameAssembler;
    private BudgetGuard budgetGuard;
    private ExpenseServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ExpenseMapper.class);
        nameAssembler = mock(NameAssembler.class);
        budgetGuard = mock(BudgetGuard.class);
        service = new ExpenseServiceImpl(mapper, nameAssembler, budgetGuard);
    }

    @Test
    @DisplayName("create - 缺少必填")
    void createMissing() {
        ExpenseCreateDTO dto = new ExpenseCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create - 负数金额拒绝")
    void createNegative() {
        ExpenseCreateDTO dto = new ExpenseCreateDTO();
        dto.setExpenseCode("E-1");
        dto.setEmployeeId(1L);
        dto.setAmount(new BigDecimal("-100"));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 成功")
    void createOk() {
        when(mapper.selectByCode("E-1")).thenReturn(null);
        when(mapper.insert(any(ExpenseDO.class))).thenAnswer(inv -> {
            ExpenseDO e = inv.getArgument(0);
            e.setId(20L);
            return 1;
        });
        ExpenseCreateDTO dto = new ExpenseCreateDTO();
        dto.setExpenseCode("E-1");
        dto.setEmployeeId(1L);
        dto.setAmount(new BigDecimal("500"));
        dto.setExpenseType("TRAVEL");
        dto.setExpenseDate(LocalDate.now());
        Long id = service.create(dto);
        org.assertj.core.api.Assertions.assertThat(id).isEqualTo(20L);
    }

    @Test
    @DisplayName("changeStatus - 终态拒绝")
    void changeStatusTerminal() {
        ExpenseDO e = new ExpenseDO();
        e.setId(1L);
        e.setStatus("PAID");
        when(mapper.selectById(1L)).thenReturn(e);
        ApprovalDTO dto = new ApprovalDTO();
        dto.setId(1L);
        dto.setTargetStatus("DRAFT");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create - 关联项目时触发预算强管控（放行）")
    void createBudgetGuardOk() {
        when(mapper.selectByCode("E-G1")).thenReturn(null);
        when(mapper.insert(any(ExpenseDO.class))).thenAnswer(inv -> {
            ExpenseDO e = inv.getArgument(0);
            e.setId(31L);
            return 1;
        });
        ExpenseCreateDTO dto = new ExpenseCreateDTO();
        dto.setExpenseCode("E-G1");
        dto.setEmployeeId(1L);
        dto.setInitiationId(200L);
        dto.setAmount(new BigDecimal("800"));
        dto.setExpenseType("TRAVEL");
        dto.setExpenseDate(LocalDate.now());
        Long id = service.create(dto);
        org.assertj.core.api.Assertions.assertThat(id).isEqualTo(31L);
        verify(budgetGuard).check(eq(200L), eq(new BigDecimal("800")), eq("EXPENSE"));
    }

    @Test
    @DisplayName("create - 预算超限阻断")
    void createBudgetGuardReject() {
        when(mapper.selectByCode("E-G2")).thenReturn(null);
        doThrow(new BizException(BizErrorCode.BAD_REQUEST, "[预算强管控] 超预算"))
                .when(budgetGuard).check(anyLong(), any(), eq("EXPENSE"));
        ExpenseCreateDTO dto = new ExpenseCreateDTO();
        dto.setExpenseCode("E-G2");
        dto.setEmployeeId(1L);
        dto.setInitiationId(200L);
        dto.setAmount(new BigDecimal("10000"));
        dto.setExpenseType("TRAVEL");
        dto.setExpenseDate(LocalDate.now());
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("预算强管控");
        verify(mapper, never()).insert(any(ExpenseDO.class));
    }

    @Test
    @DisplayName("create - 不关联项目时跳过预算校验")
    void createNoInitiationSkipGuard() {
        when(mapper.selectByCode("E-G3")).thenReturn(null);
        when(mapper.insert(any(ExpenseDO.class))).thenAnswer(inv -> {
            ExpenseDO e = inv.getArgument(0);
            e.setId(32L);
            return 1;
        });
        ExpenseCreateDTO dto = new ExpenseCreateDTO();
        dto.setExpenseCode("E-G3");
        dto.setEmployeeId(1L);
        dto.setAmount(new BigDecimal("300"));
        dto.setExpenseType("OFFICE");
        dto.setExpenseDate(LocalDate.now());
        service.create(dto);
        // 无 initiationId 不触发预算校验
        verify(budgetGuard, never()).check(anyLong(), any(), eq("EXPENSE"));
    }
}
