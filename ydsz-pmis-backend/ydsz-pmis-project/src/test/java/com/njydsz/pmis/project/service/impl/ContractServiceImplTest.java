package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.assembler.NameAssembler;
import com.njydsz.pmis.project.dto.ContractCreateDTO;
import com.njydsz.pmis.project.dto.ContractStatusDTO;
import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.mapper.ContractMapper;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContractServiceImpl 合同服务单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ContractServiceImpl 合同服务测试")
class ContractServiceImplTest {

    private ContractMapper mapper;
    private NameAssembler nameAssembler;
    private ContractServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ContractMapper.class);
        nameAssembler = mock(NameAssembler.class);
        service = new ContractServiceImpl(mapper, nameAssembler);
    }

    @Test
    @DisplayName("创建合同 - 缺少必填抛 BAD_REQUEST")
    void createMissing() {
        ContractCreateDTO dto = new ContractCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("创建合同 - 编号重复抛 DUPLICATE_KEY")
    void createDuplicate() {
        when(mapper.selectByCode("C-1")).thenReturn(new ContractDO());
        ContractCreateDTO dto = new ContractCreateDTO();
        dto.setContractCode("C-1");
        dto.setContractName("c");
        dto.setCustomerId(1L);
        dto.setContractType("FIXED_PRICE");
        dto.setTotalAmount(new BigDecimal("100"));
        dto.setOwnerId(2L);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("创建合同 - 负数金额拒绝")
    void createNegativeAmount() {
        ContractCreateDTO dto = new ContractCreateDTO();
        dto.setContractCode("C-2");
        dto.setContractName("c");
        dto.setCustomerId(1L);
        dto.setContractType("FIXED_PRICE");
        dto.setTotalAmount(new BigDecimal("-1"));
        dto.setOwnerId(2L);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("创建合同 - 到期日期早于生效日期拒绝")
    void createDateInvalid() {
        ContractCreateDTO dto = new ContractCreateDTO();
        dto.setContractCode("C-3");
        dto.setContractName("c");
        dto.setCustomerId(1L);
        dto.setContractType("FIXED_PRICE");
        dto.setTotalAmount(new BigDecimal("100"));
        dto.setOwnerId(2L);
        dto.setEffectiveDate(LocalDate.of(2026, 6, 30));
        dto.setExpireDate(LocalDate.of(2026, 1, 1));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("创建合同成功 - 默认 DRAFT + CNY + 风险评估")
    void createOk() {
        when(mapper.selectByCode("C-4")).thenReturn(null);
        when(mapper.insert(any(ContractDO.class))).thenAnswer(inv -> {
            ContractDO c = inv.getArgument(0);
            c.setId(7L);
            return 1;
        });
        ContractCreateDTO dto = new ContractCreateDTO();
        dto.setContractCode("C-4");
        dto.setContractName("云顶合同");
        dto.setCustomerId(1L);
        dto.setContractType("FIXED_PRICE");
        dto.setTotalAmount(new BigDecimal("100000"));
        dto.setOwnerId(2L);
        dto.setOwnerName("张三");
        dto.setCustomerName("客户甲");
        Long id = service.create(dto);
        assertThat(id).isEqualTo(7L);

        ArgumentCaptor<ContractDO> captor = ArgumentCaptor.forClass(ContractDO.class);
        verify(mapper).insert(captor.capture());
        ContractDO saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getCurrency()).isEqualTo("CNY");
        assertThat(saved.getRiskLevel()).isNotBlank();
        assertThat(saved.getTenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("状态迁移 - DRAFT -> SUBMITTED 合法")
    void changeStatusOk() {
        ContractDO c = new ContractDO();
        c.setId(1L);
        c.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(c);
        ContractStatusDTO dto = new ContractStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("SUBMITTED");
        service.changeStatus(dto);
        verify(mapper).updateStatus(1L, "SUBMITTED");
    }

    @Test
    @DisplayName("状态迁移 - 终态不可再迁移")
    void changeStatusTerminal() {
        ContractDO c = new ContractDO();
        c.setId(1L);
        c.setStatus("EXPIRED");
        when(mapper.selectById(1L)).thenReturn(c);
        ContractStatusDTO dto = new ContractStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("ACTIVE");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("状态迁移 - 跳级禁止")
    void changeStatusSkip() {
        ContractDO c = new ContractDO();
        c.setId(1L);
        c.setStatus("DRAFT");
        when(mapper.selectById(1L)).thenReturn(c);
        ContractStatusDTO dto = new ContractStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("ACTIVE");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("删除 - 不存在抛 NOT_FOUND")
    void deleteNotFound() {
        when(mapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("重算风险等级 - 落库并返回")
    void evaluateRisk() {
        ContractDO c = new ContractDO();
        c.setId(1L);
        c.setStatus("ACTIVE");
        c.setContractType("FIXED_PRICE");
        c.setTotalAmount(new BigDecimal("1000000"));
        c.setPaymentTerms("NET_360");
        when(mapper.selectById(1L)).thenReturn(c);
        String level = service.evaluateRisk(1L);
        assertThat(level).isIn("LOW", "MEDIUM", "HIGH");
        verify(mapper).updateById(c);
    }

    @Test
    @DisplayName("assembleNames - 已有名称不调用 Feign")
    void assembleNamesSkip() {
        ContractDO c = new ContractDO();
        c.setId(99L);
        c.setCustomerId(1L);
        c.setCustomerName("已有客户");
        c.setOwnerId(2L);
        c.setOwnerName("已有负责人");
        when(mapper.selectById(99L)).thenReturn(c);
        ContractDO got = service.getById(99L);
        assertThat(got).isNotNull();
        assertThat(got.getCustomerName()).isEqualTo("已有客户");
        assertThat(got.getOwnerName()).isEqualTo("已有负责人");
        verify(nameAssembler, never()).resolveCustomer(any());
        verify(nameAssembler, never()).resolveEmployee(any());
    }

    @Test
    @DisplayName("assembleNames - 缺名称时 Feign 补齐")
    void assembleNamesFill() {
        ContractDO c = new ContractDO();
        c.setId(1L);
        c.setCustomerId(1L);
        c.setOwnerId(2L);
        when(mapper.selectById(1L)).thenReturn(c);
        when(nameAssembler.resolveCustomer(1L)).thenReturn("客户A");
        when(nameAssembler.resolveEmployee(2L)).thenReturn("员工B");
        ContractDO got = service.getById(1L);
        assertThat(got.getCustomerName()).isEqualTo("客户A");
        assertThat(got.getOwnerName()).isEqualTo("员工B");
    }

    @Test
    @DisplayName("分页 - 对结果集装配名称")
    void pageAssemble() {
        ContractDO c = new ContractDO();
        c.setId(1L);
        c.setCustomerId(10L);
        c.setOwnerId(20L);
        Page<ContractDO> p = new Page<>();
        p.setRecords(List.of(c));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(p);
        when(nameAssembler.resolveCustomer(10L)).thenReturn("客户P");
        when(nameAssembler.resolveEmployee(20L)).thenReturn("员工Q");
        Page<ContractDO> r = service.page(1, 10, null, null, null, null);
        assertThat(r.getRecords()).hasSize(1);
        assertThat(r.getRecords().get(0).getCustomerName()).isEqualTo("客户P");
        assertThat(r.getRecords().get(0).getOwnerName()).isEqualTo("员工Q");
    }
}
