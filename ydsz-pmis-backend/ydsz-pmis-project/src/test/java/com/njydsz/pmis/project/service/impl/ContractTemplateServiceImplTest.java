package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.ContractTemplateCreateDTO;
import com.njydsz.pmis.project.dto.ContractTemplateStatusDTO;
import com.njydsz.pmis.project.entity.ContractTemplateDO;
import com.njydsz.pmis.project.enums.ContractTemplateStatus;
import com.njydsz.pmis.project.mapper.ContractTemplateMapper;
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

/**
 * ContractTemplateServiceImpl 测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ContractTemplateServiceImpl 合同模板服务")
class ContractTemplateServiceImplTest {

    private ContractTemplateMapper mapper;
    private ContractTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ContractTemplateMapper.class);
        service = new ContractTemplateServiceImpl(mapper);
    }

    @Test
    @DisplayName("创建模板-空请求")
    void createNull() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建模板-类型不合法")
    void createBadType() {
        ContractTemplateCreateDTO dto = new ContractTemplateCreateDTO();
        dto.setTemplateCode("T-1");
        dto.setTemplateName("n");
        dto.setContractType("XXX");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("创建模板-账期负数")
    void createNegativeDays() {
        ContractTemplateCreateDTO dto = new ContractTemplateCreateDTO();
        dto.setTemplateCode("T-1");
        dto.setTemplateName("n");
        dto.setContractType("FIXED_PRICE");
        dto.setDefaultPaymentDays(-1);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建模板-违约金比例越界")
    void createBadPenalty() {
        ContractTemplateCreateDTO dto = new ContractTemplateCreateDTO();
        dto.setTemplateCode("T-1");
        dto.setTemplateName("n");
        dto.setContractType("FIXED_PRICE");
        dto.setDefaultPenaltyRate(new BigDecimal("1.5"));
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建模板-编号重复")
    void createDuplicate() {
        when(mapper.selectByCode("T-1")).thenReturn(new ContractTemplateDO());
        ContractTemplateCreateDTO dto = new ContractTemplateCreateDTO();
        dto.setTemplateCode("T-1");
        dto.setTemplateName("n");
        dto.setContractType("FIXED_PRICE");
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("创建模板-成功")
    void createSuccess() {
        when(mapper.selectByCode("T-1")).thenReturn(null);
        when(mapper.insert(any(ContractTemplateDO.class))).thenAnswer(inv -> {
            ContractTemplateDO d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });
        ContractTemplateCreateDTO dto = new ContractTemplateCreateDTO();
        dto.setTemplateCode("T-1");
        dto.setTemplateName("n");
        dto.setContractType("FIXED_PRICE");
        Long id = service.create(dto);
        assertThat(id).isEqualTo(1L);
        ArgumentCaptor<ContractTemplateDO> capt = ArgumentCaptor.forClass(ContractTemplateDO.class);
        verify(mapper).insert(capt.capture());
        assertThat(capt.getValue().getStatus()).isEqualTo(ContractTemplateStatus.DRAFT.getCode());
        assertThat(capt.getValue().getVersion()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("状态迁移-非法目标")
    void changeStatusBad() {
        when(mapper.selectById(1L)).thenReturn(tpl(1L, "DRAFT"));
        ContractTemplateStatusDTO dto = new ContractTemplateStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("XXX");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("状态迁移-DRAFT->PUBLISHED")
    void changeStatusDraft2Pub() {
        when(mapper.selectById(1L)).thenReturn(tpl(1L, "DRAFT"));
        when(mapper.updateStatus(1L, "PUBLISHED")).thenReturn(1);
        ContractTemplateStatusDTO dto = new ContractTemplateStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("PUBLISHED");
        service.changeStatus(dto);
        verify(mapper).updateStatus(1L, "PUBLISHED");
    }

    @Test
    @DisplayName("状态迁移-DEPRECATED 不能迁移")
    void changeStatusFromTerminal() {
        when(mapper.selectById(1L)).thenReturn(tpl(1L, "DEPRECATED"));
        ContractTemplateStatusDTO dto = new ContractTemplateStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("DRAFT");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("删除-PUBLISHED 拒绝")
    void deletePublished() {
        when(mapper.selectById(1L)).thenReturn(tpl(1L, "PUBLISHED"));
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("删除-DRAFT 成功")
    void deleteDraft() {
        when(mapper.selectById(1L)).thenReturn(tpl(1L, "DRAFT"));
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }

    @Test
    @DisplayName("getById-不存在")
    void getByIdNotFound() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("分页查询")
    void page() {
        Page<ContractTemplateDO> p = new Page<>();
        when(mapper.selectPage(any(Page.class), any())).thenReturn(p);
        Page<ContractTemplateDO> r = service.page(1, 10, null, "FIXED_PRICE", "PUBLISHED");
        assertThat(r).isNotNull();
    }

    @Test
    @DisplayName("按类型查询")
    void listByType() {
        when(mapper.selectByType("FIXED_PRICE", "PUBLISHED")).thenReturn(java.util.List.of(tpl(1L, "PUBLISHED")));
        assertThat(service.listByType("FIXED_PRICE", "PUBLISHED")).hasSize(1);
    }

    private ContractTemplateDO tpl(Long id, String status) {
        ContractTemplateDO t = new ContractTemplateDO();
        t.setId(id);
        t.setTemplateCode("T-" + id);
        t.setTemplateName("n");
        t.setContractType("FIXED_PRICE");
        t.setStatus(status);
        return t;
    }
}
