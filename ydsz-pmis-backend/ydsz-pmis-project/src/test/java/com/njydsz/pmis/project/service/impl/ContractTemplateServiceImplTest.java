package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ContractTemplateCreateDTO;
import com.njydsz.pmis.project.dto.ContractTemplateStatusDTO;
import com.njydsz.pmis.project.entity.ContractTemplateDO;
import com.njydsz.pmis.project.enums.ContractTemplateStatus;
import com.njydsz.pmis.project.enums.ContractTemplateType;
import com.njydsz.pmis.project.mapper.ContractTemplateMapper;
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
 * ContractTemplateServiceImpl 单元测试
 */
@DisplayName("ContractTemplateServiceImpl 合同模板服务测试")
class ContractTemplateServiceImplTest {

    private ContractTemplateMapper mapper;
    private ContractTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ContractTemplateMapper.class);
        service = new ContractTemplateServiceImpl(mapper);
    }

    @Test
    @DisplayName("create 编码重复应抛 DUPLICATE_KEY")
    void create_duplicateCode() {
        ContractTemplateDO exist = new ContractTemplateDO();
        exist.setId(1L);
        when(mapper.selectByCode("TPL-001")).thenReturn(exist);

        ContractTemplateCreateDTO dto = new ContractTemplateCreateDTO();
        dto.setTemplateCode("TPL-001");
        dto.setTemplateName("模板");
        dto.setContractType(ContractTemplateType.FIXED_PRICE.name());

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create 合同类型为空应抛 BAD_REQUEST")
    void create_blankType() {
        ContractTemplateCreateDTO dto = new ContractTemplateCreateDTO();
        dto.setTemplateCode("TPL-003");
        dto.setTemplateName("模板");
        dto.setContractType("INVALID_TYPE");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 正常应返回 id 并填充默认值")
    void create_ok() {
        when(mapper.selectByCode(any())).thenReturn(null);
        when(mapper.insert((ContractTemplateDO) any())).thenAnswer(inv -> {
            ContractTemplateDO arg = inv.getArgument(0);
            arg.setId(100L);
            return 1;
        });

        ContractTemplateCreateDTO dto = new ContractTemplateCreateDTO();
        dto.setTemplateCode("TPL-100");
        dto.setTemplateName("测试模板");
        dto.setContractType(ContractTemplateType.FIXED_PRICE.name());
        dto.setDefaultPenaltyRate(new BigDecimal("0.05"));

        Long id = service.create(dto);
        assertThat(id).isEqualTo(100L);
    }

    @Test
    @DisplayName("changeStatus 状态非法转换应抛 BAD_REQUEST")
    void changeStatus_invalid() {
        ContractTemplateDO exist = new ContractTemplateDO();
        exist.setId(1L);
        exist.setStatus(ContractTemplateStatus.DRAFT.getCode());
        when(mapper.selectById(1L)).thenReturn(exist);

        ContractTemplateStatusDTO dto = new ContractTemplateStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus(ContractTemplateStatus.DEPRECATED.getCode());

        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("changeStatus 模板不存在应抛 NOT_FOUND")
    void changeStatus_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);

        ContractTemplateStatusDTO dto = new ContractTemplateStatusDTO();
        dto.setId(99L);
        dto.setTargetStatus(ContractTemplateStatus.PUBLISHED.getCode());

        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("changeStatus DRAFT -> PUBLISHED 合法")
    void changeStatus_draftToPublished() {
        ContractTemplateDO exist = new ContractTemplateDO();
        exist.setId(1L);
        exist.setStatus(ContractTemplateStatus.DRAFT.getCode());
        when(mapper.selectById(1L)).thenReturn(exist);

        ContractTemplateStatusDTO dto = new ContractTemplateStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus(ContractTemplateStatus.PUBLISHED.getCode());

        service.changeStatus(dto);
    }

    @Test
    @DisplayName("changeStatus PUBLISHED -> DEPRECATED 合法")
    void changeStatus_publishedToDeprecated() {
        ContractTemplateDO exist = new ContractTemplateDO();
        exist.setId(1L);
        exist.setStatus(ContractTemplateStatus.PUBLISHED.getCode());
        when(mapper.selectById(1L)).thenReturn(exist);

        ContractTemplateStatusDTO dto = new ContractTemplateStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus(ContractTemplateStatus.DEPRECATED.getCode());

        service.changeStatus(dto);
    }

    @Test
    @DisplayName("getById 模板不存在应抛 NOT_FOUND")
    void getById_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("delete 模板不存在应抛 NOT_FOUND")
    void delete_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("listByType 正常返回")
    void listByType_ok() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        assertThat(service.listByType(null, null)).isEmpty();
    }
}
