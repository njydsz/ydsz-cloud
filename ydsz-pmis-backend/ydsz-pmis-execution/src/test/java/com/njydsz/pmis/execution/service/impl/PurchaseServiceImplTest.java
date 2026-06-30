package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.ApprovalDTO;
import com.njydsz.pmis.execution.dto.PurchaseCreateDTO;
import com.njydsz.pmis.execution.engine.BudgetGuard;
import com.njydsz.pmis.execution.entity.PurchaseDO;
import com.njydsz.pmis.execution.mapper.PurchaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PurchaseServiceImpl 采购服务测试")
class PurchaseServiceImplTest {

    private PurchaseMapper mapper;
    private BudgetGuard budgetGuard;
    private PurchaseServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(PurchaseMapper.class);
        budgetGuard = mock(BudgetGuard.class);
        service = new PurchaseServiceImpl(mapper, budgetGuard);
    }

    @Test
    @DisplayName("create - 缺少必填")
    void createMissing() {
        PurchaseCreateDTO dto = new PurchaseCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create - 编号重复")
    void createDuplicate() {
        when(mapper.selectByCode("P-1")).thenReturn(new PurchaseDO());
        PurchaseCreateDTO dto = new PurchaseCreateDTO();
        dto.setPurchaseCode("P-1");
        dto.setItemName("服务器");
        dto.setInitiationId(1L);
        dto.setApplicantId(2L);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create - 自动计算金额")
    void createOk() {
        when(mapper.selectByCode("P-2")).thenReturn(null);
        when(mapper.insert(any(PurchaseDO.class))).thenAnswer(inv -> {
            PurchaseDO p = inv.getArgument(0);
            p.setId(11L);
            return 1;
        });
        PurchaseCreateDTO dto = new PurchaseCreateDTO();
        dto.setPurchaseCode("P-2");
        dto.setItemName("服务器");
        dto.setInitiationId(1L);
        dto.setApplicantId(2L);
        dto.setQuantity(new BigDecimal("2"));
        dto.setUnitPrice(new BigDecimal("5000"));
        Long id = service.create(dto);
        assertThat(id).isEqualTo(11L);

        ArgumentCaptor<PurchaseDO> captor = ArgumentCaptor.forClass(PurchaseDO.class);
        verify(mapper).insert(captor.capture());
        PurchaseDO saved = captor.getValue();
        assertThat(saved.getAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("changeStatus - 终态不能迁移")
    void changeStatusTerminal() {
        PurchaseDO p = new PurchaseDO();
        p.setId(1L);
        p.setStatus("PAID");
        when(mapper.selectById(1L)).thenReturn(p);
        ApprovalDTO dto = new ApprovalDTO();
        dto.setId(1L);
        dto.setTargetStatus("DRAFT");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("changeStatus - DRAFT -> SUBMITTED 合法")
    void changeStatusOk() {
        PurchaseDO p = new PurchaseDO();
        p.setId(1L);
        p.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(p);
        ApprovalDTO dto = new ApprovalDTO();
        dto.setId(1L);
        dto.setTargetStatus("SUBMITTED");
        service.changeStatus(dto);
        verify(mapper).updateStatus(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("SUBMITTED"), any(), any());
    }

    @Test
    @DisplayName("create - 触发预算强管控（成功放行）")
    void createBudgetGuardOk() {
        when(mapper.selectByCode("P-G1")).thenReturn(null);
        when(mapper.insert(any(PurchaseDO.class))).thenAnswer(inv -> {
            PurchaseDO p = inv.getArgument(0);
            p.setId(21L);
            return 1;
        });
        PurchaseCreateDTO dto = new PurchaseCreateDTO();
        dto.setPurchaseCode("P-G1");
        dto.setItemName("服务器");
        dto.setInitiationId(100L);
        dto.setApplicantId(2L);
        dto.setAmount(new BigDecimal("5000"));
        Long id = service.create(dto);
        assertThat(id).isEqualTo(21L);
        // 校验 BudgetGuard 已被调用
        verify(budgetGuard).check(eq(100L), eq(new BigDecimal("5000")), eq("PURCHASE"));
    }

    @Test
    @DisplayName("create - 预算超限阻断（抛 BizException 且不落库）")
    void createBudgetGuardReject() {
        when(mapper.selectByCode("P-G2")).thenReturn(null);
        doThrow(new BizException(BizErrorCode.BAD_REQUEST, "[预算强管控] 累计 11000 已超出预算 10000"))
                .when(budgetGuard).check(anyLong(), any(), eq("PURCHASE"));
        PurchaseCreateDTO dto = new PurchaseCreateDTO();
        dto.setPurchaseCode("P-G2");
        dto.setItemName("服务器");
        dto.setInitiationId(100L);
        dto.setApplicantId(2L);
        dto.setAmount(new BigDecimal("5000"));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("预算强管控");
        // 不应落库
        verify(mapper, never()).insert(any(PurchaseDO.class));
    }

    @Test
    @DisplayName("create - 金额为 0 不触发预算校验")
    void createZeroAmountSkipGuard() {
        when(mapper.selectByCode("P-G3")).thenReturn(null);
        when(mapper.insert(any(PurchaseDO.class))).thenAnswer(inv -> {
            PurchaseDO p = inv.getArgument(0);
            p.setId(22L);
            return 1;
        });
        PurchaseCreateDTO dto = new PurchaseCreateDTO();
        dto.setPurchaseCode("P-G3");
        dto.setItemName("免费样件");
        dto.setInitiationId(100L);
        dto.setApplicantId(2L);
        dto.setAmount(BigDecimal.ZERO);
        service.create(dto);
        // amount=0 不触发预算校验
        verify(budgetGuard, never()).check(anyLong(), any(), eq("PURCHASE"));
    }
}
