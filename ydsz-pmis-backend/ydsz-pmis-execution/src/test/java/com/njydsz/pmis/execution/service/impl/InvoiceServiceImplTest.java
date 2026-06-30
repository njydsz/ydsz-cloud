package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.InvoiceApprovalDTO;
import com.njydsz.pmis.execution.dto.InvoiceCreateDTO;
import com.njydsz.pmis.execution.entity.InvoiceDO;
import com.njydsz.pmis.execution.mapper.InvoiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InvoiceServiceImpl 发票服务测试")
class InvoiceServiceImplTest {

    private InvoiceMapper mapper;
    private InvoiceServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(InvoiceMapper.class);
        service = new InvoiceServiceImpl(mapper);
    }

    private InvoiceCreateDTO valid(String code) {
        InvoiceCreateDTO d = new InvoiceCreateDTO();
        d.setInvoiceCode(code);
        d.setInvoiceType("NORMAL");
        d.setContractId(1L);
        d.setInitiationId(1L);
        d.setCustomerId(1L);
        d.setInvoiceBasis("OTHER");
        d.setAmount(new BigDecimal("1000"));
        d.setCurrency("CNY");
        return d;
    }

    @Test
    @DisplayName("create 缺少 code")
    void createMissingCode() {
        InvoiceCreateDTO d = valid("X");
        d.setInvoiceCode("");
        assertThatThrownBy(() -> service.create(d)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 金额非法")
    void createInvalidAmount() {
        InvoiceCreateDTO d = valid("X");
        d.setAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.create(d)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 发票类型非法")
    void createInvalidType() {
        InvoiceCreateDTO d = valid("X");
        d.setInvoiceType("XXX");
        assertThatThrownBy(() -> service.create(d)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 开票依据非法")
    void createInvalidBasis() {
        InvoiceCreateDTO d = valid("X");
        d.setInvoiceBasis("XXX");
        assertThatThrownBy(() -> service.create(d)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 编号重复")
    void createDuplicate() {
        when(mapper.selectByCode("X")).thenReturn(new InvoiceDO());
        assertThatThrownBy(() -> service.create(valid("X"))).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 里程碑开票缺验收报告")
    void createMilestoneMissingProof() {
        when(mapper.selectByCode("X")).thenReturn(null);
        InvoiceCreateDTO d = valid("X");
        d.setInvoiceBasis("MILESTONE");
        assertThatThrownBy(() -> service.create(d)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 外包开票缺人天单")
    void createOutsourcingMissingProof() {
        when(mapper.selectByCode("X")).thenReturn(null);
        InvoiceCreateDTO d = valid("X");
        d.setInvoiceBasis("OUTSOURCING");
        assertThatThrownBy(() -> service.create(d)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 红冲需 reversedById")
    void createRedReverseMissingId() {
        when(mapper.selectByCode("X")).thenReturn(null);
        InvoiceCreateDTO d = valid("X");
        d.setInvoiceType("RED_REVERSE");
        assertThatThrownBy(() -> service.create(d)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 红冲金额大于原票")
    void createRedReverseTooLarge() {
        InvoiceDO orig = new InvoiceDO();
        orig.setId(99L);
        orig.setStatus("ISSUED");
        orig.setAmount(new BigDecimal("1000"));
        when(mapper.selectByCode("X")).thenReturn(null);
        when(mapper.selectById(99L)).thenReturn(orig);
        InvoiceCreateDTO d = valid("X");
        d.setInvoiceType("RED_REVERSE");
        d.setReversedById(99L);
        d.setAmount(new BigDecimal("2000"));
        assertThatThrownBy(() -> service.create(d)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("create 成功 价税分离")
    void createOk() {
        when(mapper.selectByCode("X")).thenReturn(null);
        when(mapper.insert(any(InvoiceDO.class))).thenAnswer(inv -> {
            InvoiceDO d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });
        Long id = service.create(valid("X"));
        ArgumentCaptor<InvoiceDO> capt = ArgumentCaptor.forClass(InvoiceDO.class);
        verify(mapper).insert(capt.capture());
        InvoiceDO d = capt.getValue();
        assertThat(d.getStatus()).isEqualTo("DRAFT");
        assertThat(d.getNetAmount()).isEqualByComparingTo("943.40");
        assertThat(d.getTaxAmount()).isEqualByComparingTo("56.60");
        assertThat(id).isEqualTo(1L);
    }

    @Test
    @DisplayName("submit 状态机非法")
    void submitInvalid() {
        InvoiceDO inv = new InvoiceDO();
        inv.setId(1L);
        inv.setStatus("ISSUED");
        when(mapper.selectById(1L)).thenReturn(inv);
        assertThatThrownBy(() -> service.submit(1L, 100L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("approve 缺 operatorId")
    void approveMissingOperator() {
        InvoiceDO inv = new InvoiceDO();
        inv.setId(1L);
        inv.setStatus("SUBMITTED");
        when(mapper.selectById(1L)).thenReturn(inv);
        assertThatThrownBy(() -> service.approve(1L, new InvoiceApprovalDTO()))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("issue 缺发票号")
    void issueMissingInvoiceNo() {
        InvoiceDO inv = new InvoiceDO();
        inv.setId(1L);
        inv.setStatus("APPROVED");
        when(mapper.selectById(1L)).thenReturn(inv);
        InvoiceApprovalDTO dto = new InvoiceApprovalDTO();
        dto.setOperatorId(1L);
        assertThatThrownBy(() -> service.issue(1L, dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("redReverse 非 NORMAL 不能红冲")
    void redReverseNotNormal() {
        InvoiceDO inv = new InvoiceDO();
        inv.setId(1L);
        inv.setStatus("ISSUED");
        inv.setInvoiceType("RED_REVERSE");
        when(mapper.selectById(1L)).thenReturn(inv);
        assertThatThrownBy(() -> service.redReverse(1L, 100L, "x"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("delete 仅 DRAFT/REJECTED/CANCELLED 可删")
    void deleteOnlyDraft() {
        InvoiceDO inv = new InvoiceDO();
        inv.setId(1L);
        inv.setStatus("ISSUED");
        when(mapper.selectById(1L)).thenReturn(inv);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BizException.class);
    }
}
