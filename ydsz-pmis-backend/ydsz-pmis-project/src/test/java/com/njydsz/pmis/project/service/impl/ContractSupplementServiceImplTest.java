package com.njydsz.pmis.project.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ContractSupplementDTO;
import com.njydsz.pmis.project.entity.ContractDO;
import com.njydsz.pmis.project.entity.ContractSupplementDO;
import com.njydsz.pmis.project.mapper.ContractMapper;
import com.njydsz.pmis.project.mapper.ContractSupplementMapper;
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
 * ContractSupplementServiceImpl 合同补充协议服务单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ContractSupplementServiceImpl 补充协议测试")
class ContractSupplementServiceImplTest {

    private ContractSupplementMapper supplementMapper;
    private ContractMapper contractMapper;
    private ContractSupplementServiceImpl service;

    @BeforeEach
    void setUp() {
        supplementMapper = mock(ContractSupplementMapper.class);
        contractMapper = mock(ContractMapper.class);
        service = new ContractSupplementServiceImpl(supplementMapper, contractMapper);
    }

    @Test
    @DisplayName("create DTO 为空应抛 BAD_REQUEST")
    void create_null() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 合同 ID 为空应抛 BAD_REQUEST")
    void create_nullContractId() {
        ContractSupplementDTO dto = new ContractSupplementDTO();
        dto.setSupplementCode("SUP-001");
        dto.setSupplementName("协议1");
        dto.setSupplementType("AMOUNT");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 补充协议编号为空应抛 BAD_REQUEST")
    void create_blankCode() {
        ContractSupplementDTO dto = new ContractSupplementDTO();
        dto.setContractId(1L);
        dto.setSupplementCode(" ");
        dto.setSupplementName("协议1");
        dto.setSupplementType("AMOUNT");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 名称为空应抛 BAD_REQUEST")
    void create_blankName() {
        ContractSupplementDTO dto = new ContractSupplementDTO();
        dto.setContractId(1L);
        dto.setSupplementCode("SUP-001");
        dto.setSupplementName(" ");
        dto.setSupplementType("AMOUNT");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 类型非法应抛 BAD_REQUEST")
    void create_invalidType() {
        ContractSupplementDTO dto = new ContractSupplementDTO();
        dto.setContractId(1L);
        dto.setSupplementCode("SUP-001");
        dto.setSupplementName("协议1");
        dto.setSupplementType("INVALID");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 合同不存在应抛 NOT_FOUND")
    void create_contractNotFound() {
        ContractSupplementDTO dto = validDto();
        when(contractMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("create 补充协议编号重复应抛 DUPLICATE_KEY")
    void create_duplicateCode() {
        ContractSupplementDTO dto = validDto();
        when(contractMapper.selectById(1L)).thenReturn(new ContractDO());
        when(supplementMapper.selectByCode("SUP-001")).thenReturn(new ContractSupplementDO());
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create AMOUNT 类型应联动主合同金额并回填 newTotalAmount")
    void create_amountLink() {
        ContractSupplementDTO dto = validDto();
        dto.setChangeAmount(new BigDecimal("5000"));
        ContractDO contract = new ContractDO();
        contract.setId(1L);
        ContractDO refreshed = new ContractDO();
        refreshed.setId(1L);
        refreshed.setTotalAmount(new BigDecimal("105000"));
        when(contractMapper.selectById(1L)).thenReturn(contract, refreshed);
        when(supplementMapper.selectByCode("SUP-001")).thenReturn(null);
        when(supplementMapper.insert(any(ContractSupplementDO.class))).thenAnswer(inv -> {
            ContractSupplementDO arg = inv.getArgument(0);
            arg.setId(300L);
            return 1;
        });

        Long id = service.create(dto);
        assertThat(id).isEqualTo(300L);
    }

    @Test
    @DisplayName("create AMOUNT 类型 changeAmount 为 0 不联动")
    void create_amountZero() {
        ContractSupplementDTO dto = validDto();
        dto.setChangeAmount(BigDecimal.ZERO);
        when(contractMapper.selectById(1L)).thenReturn(new ContractDO());
        when(supplementMapper.selectByCode("SUP-001")).thenReturn(null);
        when(supplementMapper.insert(any(ContractSupplementDO.class))).thenAnswer(inv -> {
            ContractSupplementDO arg = inv.getArgument(0);
            arg.setId(301L);
            return 1;
        });

        Long id = service.create(dto);
        assertThat(id).isEqualTo(301L);
    }

    @Test
    @DisplayName("create SCOPE 类型不联动金额")
    void create_scope() {
        ContractSupplementDTO dto = validDto();
        dto.setSupplementType("SCOPE");
        when(contractMapper.selectById(1L)).thenReturn(new ContractDO());
        when(supplementMapper.selectByCode("SUP-001")).thenReturn(null);
        when(supplementMapper.insert(any(ContractSupplementDO.class))).thenAnswer(inv -> {
            ContractSupplementDO arg = inv.getArgument(0);
            arg.setId(302L);
            return 1;
        });

        Long id = service.create(dto);
        assertThat(id).isEqualTo(302L);
    }

    @Test
    @DisplayName("delete 不存在应抛 NOT_FOUND")
    void delete_notFound() {
        when(supplementMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("delete 存在应正常删除")
    void delete_ok() {
        ContractSupplementDO s = new ContractSupplementDO();
        s.setId(1L);
        when(supplementMapper.selectById(1L)).thenReturn(s);
        service.delete(1L);
    }

    @Test
    @DisplayName("getById 不存在应抛 NOT_FOUND")
    void getById_notFound() {
        when(supplementMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("listByContract contractId 为空应返回空列表")
    void listByContract_null() {
        assertThat(service.listByContract(null)).isEmpty();
    }

    private ContractSupplementDTO validDto() {
        ContractSupplementDTO dto = new ContractSupplementDTO();
        dto.setContractId(1L);
        dto.setSupplementCode("SUP-001");
        dto.setSupplementName("协议1");
        dto.setSupplementType("AMOUNT");
        return dto;
    }
}
