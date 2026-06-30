package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.PaymentAllocationDTO;
import com.njydsz.pmis.execution.dto.PaymentCreateDTO;
import com.njydsz.pmis.execution.entity.InvoiceDO;
import com.njydsz.pmis.execution.entity.PaymentDO;
import com.njydsz.pmis.execution.mapper.InvoiceMapper;
import com.njydsz.pmis.execution.mapper.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PaymentServiceImpl 回款服务测试")
class PaymentServiceImplTest {

    private PaymentMapper paymentMapper;
    private InvoiceMapper invoiceMapper;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        paymentMapper = mock(PaymentMapper.class);
        invoiceMapper = mock(InvoiceMapper.class);
        service = new PaymentServiceImpl(paymentMapper, invoiceMapper);
    }

    private PaymentCreateDTO valid(String code) {
        PaymentCreateDTO d = new PaymentCreateDTO();
        d.setPaymentCode(code);
        d.setContractId(1L);
        d.setInitiationId(1L);
        d.setCustomerId(1L);
        d.setAmount(new BigDecimal("1000"));
        d.setPaymentDate(LocalDate.now());
        d.setCurrency("CNY");
        return d;
    }

    @Test
    @DisplayName("record 缺编号")
    void recordMissingCode() {
        PaymentCreateDTO d = valid("X");
        d.setPaymentCode("");
        assertThatThrownBy(() -> service.record(d)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("record 缺到账日期")
    void recordMissingDate() {
        PaymentCreateDTO d = valid("X");
        d.setPaymentDate(null);
        assertThatThrownBy(() -> service.record(d)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("record 编号重复")
    void recordDuplicate() {
        when(paymentMapper.selectByCode("X")).thenReturn(new PaymentDO());
        assertThatThrownBy(() -> service.record(valid("X"))).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("record 成功 已分配预分配")
    void recordOk() {
        when(paymentMapper.selectByCode("X")).thenReturn(null);
        when(paymentMapper.insert(any(PaymentDO.class))).thenAnswer(inv -> {
            PaymentDO d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });
        PaymentCreateDTO d = valid("X");
        d.setAllocatedAmount(new BigDecimal("300"));
        Long id = service.record(d);
        ArgumentCaptor<PaymentDO> capt = ArgumentCaptor.forClass(PaymentDO.class);
        verify(paymentMapper).insert(capt.capture());
        PaymentDO saved = capt.getValue();
        assertThat(saved.getUnallocatedAmount()).isEqualByComparingTo("700");
        assertThat(id).isEqualTo(1L);
    }

    @Test
    @DisplayName("confirm 状态机非法")
    void confirmInvalid() {
        PaymentDO p = new PaymentDO();
        p.setId(1L);
        p.setStatus("ALLOCATED");
        when(paymentMapper.selectById(1L)).thenReturn(p);
        assertThatThrownBy(() -> service.confirm(1L, 100L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("cancel 已核销回款不能取消")
    void cancelAllocated() {
        PaymentDO p = new PaymentDO();
        p.setId(1L);
        p.setStatus("CONFIRMED");
        p.setAllocatedAmount(new BigDecimal("100"));
        when(paymentMapper.selectById(1L)).thenReturn(p);
        assertThatThrownBy(() -> service.cancel(1L, 100L, "x"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("allocate 状态非法")
    void allocateInvalidStatus() {
        PaymentDO p = new PaymentDO();
        p.setId(1L);
        p.setStatus("PENDING");
        p.setAmount(new BigDecimal("1000"));
        p.setAllocatedAmount(BigDecimal.ZERO);
        p.setUnallocatedAmount(new BigDecimal("1000"));
        when(paymentMapper.selectById(1L)).thenReturn(p);
        InvoiceDO inv = new InvoiceDO();
        inv.setId(2L);
        inv.setStatus("ISSUED");
        when(invoiceMapper.selectById(2L)).thenReturn(inv);
        PaymentAllocationDTO a = new PaymentAllocationDTO();
        a.setPaymentId(1L);
        a.setInvoiceId(2L);
        a.setAmount(new BigDecimal("500"));
        assertThatThrownBy(() -> service.allocate(a)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("allocate 核销超额")
    void allocateExceed() {
        PaymentDO p = new PaymentDO();
        p.setId(1L);
        p.setStatus("CONFIRMED");
        p.setAmount(new BigDecimal("1000"));
        p.setAllocatedAmount(BigDecimal.ZERO);
        p.setUnallocatedAmount(new BigDecimal("500"));
        when(paymentMapper.selectById(1L)).thenReturn(p);
        InvoiceDO inv = new InvoiceDO();
        inv.setId(2L);
        inv.setStatus("ISSUED");
        when(invoiceMapper.selectById(2L)).thenReturn(inv);
        PaymentAllocationDTO a = new PaymentAllocationDTO();
        a.setPaymentId(1L);
        a.setInvoiceId(2L);
        a.setAmount(new BigDecimal("800"));
        assertThatThrownBy(() -> service.allocate(a)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("allocate 全部分配转 ALLOCATED")
    void allocateFull() {
        PaymentDO p = new PaymentDO();
        p.setId(1L);
        p.setStatus("CONFIRMED");
        p.setAmount(new BigDecimal("1000"));
        p.setAllocatedAmount(BigDecimal.ZERO);
        p.setUnallocatedAmount(new BigDecimal("1000"));
        when(paymentMapper.selectById(1L)).thenReturn(p);
        InvoiceDO inv = new InvoiceDO();
        inv.setId(2L);
        inv.setStatus("ISSUED");
        when(invoiceMapper.selectById(2L)).thenReturn(inv);
        PaymentAllocationDTO a = new PaymentAllocationDTO();
        a.setPaymentId(1L);
        a.setInvoiceId(2L);
        a.setAmount(new BigDecimal("1000"));
        a.setOperatorId(100L);
        service.allocate(a);
        verify(paymentMapper).updateAllocation(eq(1L), eq("2"),
                eq(new BigDecimal("1000")), eq(BigDecimal.ZERO));
        verify(paymentMapper).updateStatus(1L, "ALLOCATED", 100L);
    }

    @Test
    @DisplayName("autoAllocate 空池")
    void autoAllocateEmpty() {
        when(paymentMapper.selectUnallocated(1L)).thenReturn(List.of());
        int n = service.autoAllocate(1L, 100L);
        assertThat(n).isZero();
    }

    @Test
    @DisplayName("forecast 范围 1-12")
    void forecastRange() {
        when(invoiceMapper.selectByInitiation(1L)).thenReturn(List.of());
        when(paymentMapper.aggregateByMonth(1L)).thenReturn(List.of());
        var r = service.forecastCashFlow(1L, 0);
        assertThat(r).isNotEmpty();
    }
}
