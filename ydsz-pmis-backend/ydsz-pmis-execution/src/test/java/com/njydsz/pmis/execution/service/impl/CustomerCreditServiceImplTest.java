package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.CreditAssessmentDTO;
import com.njydsz.pmis.execution.entity.CustomerCreditDO;
import com.njydsz.pmis.execution.entity.InvoiceDO;
import com.njydsz.pmis.execution.entity.PaymentDO;
import com.njydsz.pmis.execution.enums.CreditLevel;
import com.njydsz.pmis.execution.mapper.CustomerCreditMapper;
import com.njydsz.pmis.execution.mapper.InvoiceMapper;
import com.njydsz.pmis.execution.mapper.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CustomerCreditServiceImpl 客户信用服务测试")
class CustomerCreditServiceImplTest {

    private CustomerCreditMapper creditMapper;
    private InvoiceMapper invoiceMapper;
    private PaymentMapper paymentMapper;
    private CustomerCreditServiceImpl service;

    @BeforeEach
    void setUp() {
        creditMapper = mock(CustomerCreditMapper.class);
        invoiceMapper = mock(InvoiceMapper.class);
        paymentMapper = mock(PaymentMapper.class);
        service = new CustomerCreditServiceImpl(creditMapper, invoiceMapper, paymentMapper);
    }

    @Test
    @DisplayName("assess 缺 customerId")
    void assessMissing() {
        assertThatThrownBy(() -> service.assess(new CreditAssessmentDTO()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("assess 新客户 默认 A 等级")
    void assessNewCustomer() {
        when(invoiceMapper.selectByCustomer(1L)).thenReturn(List.of());
        when(paymentMapper.selectByCustomer(1L)).thenReturn(List.of());
        when(creditMapper.selectByCustomerId(1L)).thenReturn(null);
        when(creditMapper.insert(any(CustomerCreditDO.class))).thenAnswer(inv -> {
            ((CustomerCreditDO) inv.getArgument(0)).setId(1L);
            return 1;
        });
        CreditAssessmentDTO d = new CreditAssessmentDTO();
        d.setCustomerId(1L);
        d.setCustomerName("C-1");
        CustomerCreditDO credit = service.assess(d);
        assertThat(credit.getCreditLevel()).isEqualTo(CreditLevel.A.getCode());
        assertThat(credit.getCreditScore()).isGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("assess 老客户 全额回款 90+")
    void assessLoyalCustomer() {
        InvoiceDO inv = new InvoiceDO();
        inv.setStatus("ISSUED");
        inv.setInvoiceType("NORMAL");
        inv.setAmount(new BigDecimal("1000000"));
        PaymentDO p = new PaymentDO();
        p.setStatus("ALLOCATED");
        p.setAmount(new BigDecimal("1000000"));
        p.setAllocatedAmount(new BigDecimal("1000000"));
        when(invoiceMapper.selectByCustomer(1L)).thenReturn(List.of(inv));
        when(paymentMapper.selectByCustomer(1L)).thenReturn(List.of(p));
        when(creditMapper.selectByCustomerId(1L)).thenReturn(null);
        when(creditMapper.insert(any(CustomerCreditDO.class))).thenAnswer(i -> {
            ((CustomerCreditDO) i.getArgument(0)).setId(1L);
            return 1;
        });
        CreditAssessmentDTO d = new CreditAssessmentDTO();
        d.setCustomerId(1L);
        CustomerCreditDO c = service.assess(d);
        assertThat(c.getCreditLevel()).isIn("A", "B");
        assertThat(c.getTotalInvoicedAmount()).isEqualByComparingTo("1000000");
        assertThat(c.getTotalReceivedAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("assess 多次逾期 D 级")
    void assessOverdueCustomer() {
        InvoiceDO inv = new InvoiceDO();
        inv.setStatus("ISSUED");
        inv.setInvoiceType("NORMAL");
        inv.setAmount(new BigDecimal("10000"));
        PaymentDO p1 = new PaymentDO();
        p1.setStatus("CONFIRMED");
        p1.setAmount(new BigDecimal("10000"));
        p1.setAllocatedAmount(BigDecimal.ZERO);
        PaymentDO p2 = new PaymentDO();
        p2.setStatus("CONFIRMED");
        p2.setAmount(new BigDecimal("10000"));
        p2.setAllocatedAmount(BigDecimal.ZERO);
        when(invoiceMapper.selectByCustomer(1L)).thenReturn(List.of(inv));
        when(paymentMapper.selectByCustomer(1L)).thenReturn(List.of(p1, p2));
        when(creditMapper.selectByCustomerId(1L)).thenReturn(null);
        when(creditMapper.insert(any(CustomerCreditDO.class))).thenAnswer(i -> {
            ((CustomerCreditDO) i.getArgument(0)).setId(1L);
            return 1;
        });
        CreditAssessmentDTO d = new CreditAssessmentDTO();
        d.setCustomerId(1L);
        CustomerCreditDO c = service.assess(d);
        assertThat(c.getOverdueCount()).isEqualTo(2);
        // 0% 及时率 + 1w 规模低 + 2次合作 -10分惩罚
        assertThat(c.getCreditScore()).isLessThan(60);
    }

    @Test
    @DisplayName("assess 更新已有记录")
    void assessUpdate() {
        when(invoiceMapper.selectByCustomer(1L)).thenReturn(List.of());
        when(paymentMapper.selectByCustomer(1L)).thenReturn(List.of());
        CustomerCreditDO existing = new CustomerCreditDO();
        existing.setId(99L);
        when(creditMapper.selectByCustomerId(1L)).thenReturn(existing);
        CreditAssessmentDTO d = new CreditAssessmentDTO();
        d.setCustomerId(1L);
        CustomerCreditDO c = service.assess(d);
        verify(creditMapper).updateById(existing);
        assertThat(c.getId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("profile 风险映射")
    void profileMapping() {
        CustomerCreditDO c = new CustomerCreditDO();
        c.setCreditLevel("D");
        when(creditMapper.selectByCustomerId(1L)).thenReturn(c);
        Map<String, Object> p = service.profile(1L);
        assertThat(p.get("riskLevel")).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("distribution")
    void distribution() {
        when(creditMapper.selectByLevel("A")).thenReturn(List.of());
        when(creditMapper.selectByLevel("B")).thenReturn(List.of());
        when(creditMapper.selectByLevel("C")).thenReturn(List.of());
        when(creditMapper.selectByLevel("D")).thenReturn(List.of());
        List<Map<String, Object>> d = service.distribution();
        assertThat(d).hasSize(4);
    }

    @Test
    @DisplayName("getByCustomer null 安全")
    void getByCustomerNull() {
        when(creditMapper.selectByCustomerId(null)).thenReturn(null);
        assertThat(service.getByCustomer(null)).isNull();
    }

    @Test
    @DisplayName("listByLevel null 安全")
    void listByLevelNull() {
        assertThat(service.listByLevel(null)).isEmpty();
    }
}
