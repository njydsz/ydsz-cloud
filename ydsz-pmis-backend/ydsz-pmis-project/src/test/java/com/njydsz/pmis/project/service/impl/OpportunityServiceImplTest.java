package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.OpportunityCreateDTO;
import com.njydsz.pmis.project.dto.OpportunityStatusDTO;
import com.njydsz.pmis.project.dto.OpportunityUpdateDTO;
import com.njydsz.pmis.project.entity.OpportunityDO;
import com.njydsz.pmis.project.enums.OpportunityStatus;
import com.njydsz.pmis.project.mapper.OpportunityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OpportunityServiceImpl 商机服务测试")
class OpportunityServiceImplTest {

    private OpportunityMapper mapper;
    private OpportunityServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(OpportunityMapper.class);
        com.njydsz.pmis.project.assembler.NameAssembler assembler =
                mock(com.njydsz.pmis.project.assembler.NameAssembler.class);
        service = new OpportunityServiceImpl(mapper, assembler);
    }

    @Test
    @DisplayName("创建商机 - 缺少必填抛 BAD_REQUEST")
    void createMissing() {
        OpportunityCreateDTO dto = new OpportunityCreateDTO();
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建商机 - 编号重复抛 DUPLICATE_KEY")
    void createDuplicate() {
        when(mapper.selectByCode("O-1")).thenReturn(new OpportunityDO());
        OpportunityCreateDTO dto = new OpportunityCreateDTO();
        dto.setOpportunityCode("O-1");
        dto.setOpportunityName("x");
        dto.setCustomerId(1L);
        dto.setOwnerId(2L);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10102);
    }

    @Test
    @DisplayName("创建商机成功 - 默认 FOLLOWING + C")
    void createOk() {
        when(mapper.selectByCode("O-2")).thenReturn(null);
        when(mapper.insert(any(OpportunityDO.class))).thenAnswer(inv -> {
            OpportunityDO o = inv.getArgument(0);
            o.setId(10L);
            return 1;
        });
        OpportunityCreateDTO dto = new OpportunityCreateDTO();
        dto.setOpportunityCode("O-2");
        dto.setOpportunityName("商机甲");
        dto.setCustomerId(1L);
        dto.setOwnerId(2L);
        Long id = service.create(dto);
        assertThat(id).isEqualTo(10L);

        ArgumentCaptor<OpportunityDO> captor = ArgumentCaptor.forClass(OpportunityDO.class);
        verify(mapper).insert(captor.capture());
        OpportunityDO saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("FOLLOWING");
        assertThat(saved.getLevel()).isEqualTo("C");
        assertThat(saved.getTenantId()).isEqualTo(1L);
        assertThat(saved.getWinRate()).isNotNull();
    }

    @Test
    @DisplayName("更新商机 - 缺失 ID 抛 BAD_REQUEST")
    void updateMissingId() {
        OpportunityUpdateDTO dto = new OpportunityUpdateDTO();
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10001);
    }

    @Test
    @DisplayName("更新商机 - 不存在抛 NOT_FOUND")
    void updateNotFound() {
        when(mapper.selectById(1L)).thenReturn(null);
        OpportunityUpdateDTO dto = new OpportunityUpdateDTO();
        dto.setId(1L);
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10101);
    }

    @Test
    @DisplayName("状态迁移 - 合法 FOLLOWING -> WON")
    void transitOk() {
        OpportunityDO o = new OpportunityDO();
        o.setId(1L);
        o.setStatus("NEGOTIATING");
        when(mapper.selectById(1L)).thenReturn(o);
        OpportunityStatusDTO dto = new OpportunityStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("WON");
        service.changeStatus(dto);
        verify(mapper).updateStatus(1L, "WON", null);
    }

    @Test
    @DisplayName("状态迁移 - 终态不可再迁移")
    void transitTerminal() {
        OpportunityDO o = new OpportunityDO();
        o.setId(1L);
        o.setStatus("WON");
        when(mapper.selectById(1L)).thenReturn(o);
        OpportunityStatusDTO dto = new OpportunityStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("LOST");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10001);
    }

    @Test
    @DisplayName("状态迁移 - 跳级禁止")
    void transitSkip() {
        OpportunityDO o = new OpportunityDO();
        o.setId(1L);
        o.setStatus("FOLLOWING");
        when(mapper.selectById(1L)).thenReturn(o);
        OpportunityStatusDTO dto = new OpportunityStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("WON");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10001);
    }

    @Test
    @DisplayName("状态迁移 - 输单必填原因")
    void transitLostReason() {
        OpportunityDO o = new OpportunityDO();
        o.setId(1L);
        o.setStatus("QUOTED");
        when(mapper.selectById(1L)).thenReturn(o);
        OpportunityStatusDTO dto = new OpportunityStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("LOST");
        assertThatThrownBy(() -> service.changeStatus(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("删除 - 不存在抛 NOT_FOUND")
    void deleteNotFound() {
        when(mapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(10101);
    }

    @Test
    @DisplayName("评估赢率 - 应落库")
    void evaluateWinRate() {
        OpportunityDO o = new OpportunityDO();
        o.setId(1L);
        o.setStatus("NEGOTIATING");
        o.setLevel("B");
        when(mapper.selectById(1L)).thenReturn(o);
        BigDecimal r = service.evaluateWinRate(1L, "A", true);
        assertThat(r).isGreaterThan(BigDecimal.ZERO);
        ArgumentCaptor<OpportunityDO> captor = ArgumentCaptor.forClass(OpportunityDO.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getWinRate()).isEqualTo(r);
    }

    @Test
    @DisplayName("分页 - null owner 不影响查询")
    void pageNoOwner() {
        when(mapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());
        service.page(1, 20, "k", "FOLLOWING", "B", null);
        verify(mapper).selectPage(any(Page.class), any());
        verify(mapper, never()).selectById(any());
    }
}
