package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.entity.CostAllocationDO;
import com.njydsz.pmis.execution.mapper.CostAllocationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CostAllocationServiceImpl 成本归集服务测试")
class CostAllocationServiceImplTest {

    private CostAllocationMapper mapper;
    private CostAllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(CostAllocationMapper.class);
        service = new CostAllocationServiceImpl(mapper);
    }

    @Test
    @DisplayName("syncFromTimeEntry - 写入 LABOR 类型")
    void syncFromTimeEntry() {
        when(mapper.insert(any(CostAllocationDO.class))).thenAnswer(inv -> {
            CostAllocationDO c = inv.getArgument(0);
            c.setId(1L);
            return 1;
        });
        Long id = service.syncFromTimeEntry(100L, 1L, 2L, "张三", "L5",
                "2026-06", new BigDecimal("800"), true);
        assertThat(id).isEqualTo(1L);
        ArgumentCaptor<CostAllocationDO> captor = ArgumentCaptor.forClass(CostAllocationDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCostType()).isEqualTo("LABOR");
        assertThat(captor.getValue().getSourceType()).isEqualTo("TIME_ENTRY");
        assertThat(captor.getValue().getBillable()).isEqualTo(1);
    }

    @Test
    @DisplayName("syncFromPurchase - 写入 PURCHASE 类型")
    void syncFromPurchase() {
        when(mapper.insert(any(CostAllocationDO.class))).thenReturn(1);
        service.syncFromPurchase(200L, 1L, "2026-06", new BigDecimal("5000"), true);
        ArgumentCaptor<CostAllocationDO> captor = ArgumentCaptor.forClass(CostAllocationDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCostType()).isEqualTo("PURCHASE");
    }

    @Test
    @DisplayName("syncFromExpense - 写入 EXPENSE 类型")
    void syncFromExpense() {
        when(mapper.insert(any(CostAllocationDO.class))).thenReturn(1);
        service.syncFromExpense(300L, 1L, "2026-06", new BigDecimal("500"), false);
        ArgumentCaptor<CostAllocationDO> captor = ArgumentCaptor.forClass(CostAllocationDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCostType()).isEqualTo("EXPENSE");
        assertThat(captor.getValue().getBillable()).isEqualTo(0);
    }

    @Test
    @DisplayName("markAllocated - 标记已分摊")
    void markAllocated() {
        CostAllocationDO c = new CostAllocationDO();
        c.setId(1L);
        c.setAllocated(0);
        when(mapper.selectById(1L)).thenReturn(c);
        service.markAllocated(List.of(1L));
        assertThat(c.getAllocated()).isEqualTo(1);
        verify(mapper).updateById(c);
    }

    @Test
    @DisplayName("markAllocated - 空列表安全")
    void markAllocatedEmpty() {
        service.markAllocated(null);
        service.markAllocated(List.of());
        org.mockito.Mockito.verifyNoInteractions(mapper);
    }
}
