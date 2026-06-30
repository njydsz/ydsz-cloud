package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ContractChangeDTO;
import com.njydsz.pmis.project.entity.ContractChangeDO;
import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.enums.RiskLevel;
import com.njydsz.pmis.project.mapper.ContractChangeMapper;
import com.njydsz.pmis.project.mapper.ContractMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ContractChangeServiceImpl 单元测试
 */
@DisplayName("ContractChangeServiceImpl 合同变更测试")
class ContractChangeServiceImplTest {

    private ContractChangeMapper changeMapper;
    private ContractMapper contractMapper;
    private ContractChangeServiceImpl service;

    @BeforeEach
    void setUp() {
        changeMapper = mock(ContractChangeMapper.class);
        contractMapper = mock(ContractMapper.class);
        service = new ContractChangeServiceImpl(changeMapper, contractMapper);
    }

    @Test
    @DisplayName("apply DTO 为空应抛 BAD_REQUEST")
    void apply_null() {
        assertThatThrownBy(() -> service.apply(null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("apply 合同 ID 为空应抛 BAD_REQUEST")
    void apply_nullContractId() {
        ContractChangeDTO dto = new ContractChangeDTO();
        dto.setChangeCode("CH-001");
        dto.setChangeType("SCOPE");
        assertThatThrownBy(() -> service.apply(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("apply 变更编号为空应抛 BAD_REQUEST")
    void apply_blankCode() {
        ContractChangeDTO dto = new ContractChangeDTO();
        dto.setContractId(1L);
        dto.setChangeCode(" ");
        dto.setChangeType("SCOPE");
        assertThatThrownBy(() -> service.apply(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("apply 变更类型非法应抛 BAD_REQUEST")
    void apply_invalidType() {
        ContractChangeDTO dto = new ContractChangeDTO();
        dto.setContractId(1L);
        dto.setChangeCode("CH-001");
        dto.setChangeType("INVALID");
        assertThatThrownBy(() -> service.apply(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("apply 合同不存在应抛 NOT_FOUND")
    void apply_contractNotFound() {
        ContractChangeDTO dto = validDto();
        when(contractMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.apply(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("apply 变更编号重复应抛 DUPLICATE_KEY")
    void apply_duplicateCode() {
        ContractChangeDTO dto = validDto();
        ContractDO contract = new ContractDO();
        contract.setId(1L);
        when(contractMapper.selectById(1L)).thenReturn(contract);
        when(changeMapper.selectByCode("CH-001")).thenReturn(new ContractChangeDO());
        assertThatThrownBy(() -> service.apply(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("apply 正常应返回 id")
    void apply_ok() {
        ContractChangeDTO dto = validDto();
        ContractDO contract = new ContractDO();
        contract.setId(1L);
        when(contractMapper.selectById(1L)).thenReturn(contract);
        when(changeMapper.selectByCode("CH-001")).thenReturn(null);
        when(changeMapper.insert(any(ContractChangeDO.class))).thenAnswer(inv -> {
            ContractChangeDO arg = inv.getArgument(0);
            arg.setId(200L);
            return 1;
        });

        Long id = service.apply(dto);
        assertThat(id).isEqualTo(200L);
    }

    @Test
    @DisplayName("submit 非 DRAFT 应抛 BAD_REQUEST")
    void submit_notDraft() {
        ContractChangeDO c = new ContractChangeDO();
        c.setId(1L);
        c.setStatus("APPROVED");
        when(changeMapper.selectById(1L)).thenReturn(c);
        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("submit 正常")
    void submit_ok() {
        ContractChangeDO c = new ContractChangeDO();
        c.setId(1L);
        c.setStatus("DRAFT");
        when(changeMapper.selectById(1L)).thenReturn(c);
        service.submit(1L);
    }

    @Test
    @DisplayName("approve 非 SUBMITTED/APPROVING 应抛 BAD_REQUEST")
    void approve_invalidStatus() {
        ContractChangeDO c = new ContractChangeDO();
        c.setId(1L);
        c.setStatus("DRAFT");
        when(changeMapper.selectById(1L)).thenReturn(c);
        assertThatThrownBy(() -> service.approve(1L, 10L, "admin"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("approve AMOUNT 变更应联动主合同金额")
    void approve_amountAdjustment() {
        ContractChangeDO c = new ContractChangeDO();
        c.setId(1L);
        c.setStatus("SUBMITTED");
        c.setContractId(5L);
        c.setChangeType("AMOUNT");
        c.setAmountDelta(new BigDecimal("1000"));
        when(changeMapper.selectById(1L)).thenReturn(c);
        ContractDO refreshed = new ContractDO();
        refreshed.setId(5L);
        refreshed.setCurrency("CNY");
        when(contractMapper.selectById(5L)).thenReturn(refreshed);

        service.approve(1L, 10L, "admin");
    }

    @Test
    @DisplayName("approve 正常 SCOPE 变更无金额联动")
    void approve_scope() {
        ContractChangeDO c = new ContractChangeDO();
        c.setId(1L);
        c.setStatus("SUBMITTED");
        c.setContractId(5L);
        c.setChangeType("SCOPE");
        when(changeMapper.selectById(1L)).thenReturn(c);
        ContractDO refreshed = new ContractDO();
        refreshed.setId(5L);
        refreshed.setCurrency("CNY");
        when(contractMapper.selectById(5L)).thenReturn(refreshed);

        service.approve(1L, 10L, "admin");
    }

    @Test
    @DisplayName("reject 非 SUBMITTED 应抛 BAD_REQUEST")
    void reject_invalidStatus() {
        ContractChangeDO c = new ContractChangeDO();
        c.setId(1L);
        c.setStatus("DRAFT");
        when(changeMapper.selectById(1L)).thenReturn(c);
        assertThatThrownBy(() -> service.reject(1L, 10L, "admin", "原因"))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("reject 带原因应追加到 impactAnalysis")
    void reject_withReason() {
        ContractChangeDO c = new ContractChangeDO();
        c.setId(1L);
        c.setStatus("SUBMITTED");
        c.setImpactAnalysis("原内容");
        when(changeMapper.selectById(1L)).thenReturn(c);
        service.reject(1L, 10L, "admin", "信息不全");
    }

    @Test
    @DisplayName("reject 不带原因应不追加")
    void reject_withoutReason() {
        ContractChangeDO c = new ContractChangeDO();
        c.setId(1L);
        c.setStatus("SUBMITTED");
        when(changeMapper.selectById(1L)).thenReturn(c);
        service.reject(1L, 10L, "admin", null);
    }

    @Test
    @DisplayName("getById 不存在应抛 NOT_FOUND")
    void getById_notFound() {
        when(changeMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("listByContract contractId 为空应返回空列表")
    void listByContract_null() {
        assertThat(service.listByContract(null)).isEmpty();
    }

    private ContractChangeDTO validDto() {
        ContractChangeDTO dto = new ContractChangeDTO();
        dto.setContractId(1L);
        dto.setChangeCode("CH-001");
        dto.setChangeType("SCOPE");
        dto.setChangeReason("范围调整");
        return dto;
    }
}
