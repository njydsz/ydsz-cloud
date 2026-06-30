package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.DeliveryItemCreateDTO;
import com.njydsz.pmis.execution.dto.DeliveryItemStatusDTO;
import com.njydsz.pmis.execution.dto.DeliveryStandardCreateDTO;
import com.njydsz.pmis.execution.engine.StageGateValidator;
import com.njydsz.pmis.execution.entity.DeliveryItemDO;
import com.njydsz.pmis.execution.entity.DeliveryStandardDO;
import com.njydsz.pmis.execution.enums.DeliveryItemStatus;
import com.njydsz.pmis.execution.enums.DeliveryStage;
import com.njydsz.pmis.execution.enums.ProjectType;
import com.njydsz.pmis.execution.mapper.DeliveryItemMapper;
import com.njydsz.pmis.execution.mapper.DeliveryStandardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeliveryServiceImpl 交付物服务测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DeliveryServiceImpl 交付物服务")
class DeliveryServiceImplTest {

    private DeliveryStandardMapper standardMapper;
    private DeliveryItemMapper itemMapper;
    private DeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        standardMapper = mock(DeliveryStandardMapper.class);
        itemMapper = mock(DeliveryItemMapper.class);
        service = new DeliveryServiceImpl(standardMapper, itemMapper);
    }

    @Test
    @DisplayName("创建交付物标准-空")
    void createStandardNull() {
        assertThatThrownBy(() -> service.createStandard(null)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建交付物标准-项目类型不合法")
    void createStandardBadType() {
        DeliveryStandardCreateDTO dto = new DeliveryStandardCreateDTO();
        dto.setProjectType("XXX");
        dto.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        dto.setDeliveryName("n");
        assertThatThrownBy(() -> service.createStandard(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建交付物标准-成功")
    void createStandardSuccess() {
        when(standardMapper.insert(any(DeliveryStandardDO.class))).thenAnswer(inv -> {
            DeliveryStandardDO s = inv.getArgument(0);
            s.setId(1L);
            return 1;
        });
        DeliveryStandardCreateDTO dto = new DeliveryStandardCreateDTO();
        dto.setProjectType(ProjectType.FIXED_PRICE.getCode());
        dto.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        dto.setDeliveryName("n");
        Long id = service.createStandard(dto);
        assertThat(id).isEqualTo(1L);
        ArgumentCaptor<DeliveryStandardDO> capt = ArgumentCaptor.forClass(DeliveryStandardDO.class);
        verify(standardMapper).insert(capt.capture());
        assertThat(capt.getValue().getRequired()).isEqualTo(1);
    }

    @Test
    @DisplayName("删除标准-不存在")
    void deleteStandardNotFound() {
        when(standardMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.deleteStandard(1L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("按项目类型+等级列出")
    void listStandardsByType() {
        when(standardMapper.selectByTypeAndLevel("FIXED_PRICE", "L3"))
                .thenReturn(List.of(new DeliveryStandardDO()));
        assertThat(service.listStandards("FIXED_PRICE", "L3", null)).hasSize(1);
    }

    @Test
    @DisplayName("按阶段列出")
    void listStandardsByStage() {
        when(standardMapper.selectByStage("FIXED_PRICE", "L3", "S1"))
                .thenReturn(List.of(new DeliveryStandardDO()));
        assertThat(service.listStandards("FIXED_PRICE", "L3", "S1")).hasSize(1);
    }

    @Test
    @DisplayName("按类型统计")
    void countStandards() {
        when(standardMapper.countByType("FIXED_PRICE")).thenReturn(8L);
        assertThat(service.countStandardsByType("FIXED_PRICE")).isEqualTo(8L);
        assertThat(service.countStandardsByType("")).isEqualTo(0L);
    }

    @Test
    @DisplayName("创建交付物实例-空")
    void createItemNull() {
        assertThatThrownBy(() -> service.createItem(null)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建交付物实例-缺项目ID")
    void createItemNoProject() {
        DeliveryItemCreateDTO dto = new DeliveryItemCreateDTO();
        dto.setItemCode("D-1");
        assertThatThrownBy(() -> service.createItem(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建交付物实例-编号重复")
    void createItemDuplicate() {
        when(itemMapper.selectByCode("D-1")).thenReturn(new DeliveryItemDO());
        DeliveryItemCreateDTO dto = validItem("D-1");
        assertThatThrownBy(() -> service.createItem(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("创建交付物实例-成功")
    void createItemSuccess() {
        when(itemMapper.selectByCode("D-1")).thenReturn(null);
        when(itemMapper.insert(any(DeliveryItemDO.class))).thenAnswer(inv -> {
            DeliveryItemDO d = inv.getArgument(0);
            d.setId(1L);
            return 1;
        });
        Long id = service.createItem(validItem("D-1"));
        assertThat(id).isEqualTo(1L);
        ArgumentCaptor<DeliveryItemDO> capt = ArgumentCaptor.forClass(DeliveryItemDO.class);
        verify(itemMapper).insert(capt.capture());
        assertThat(capt.getValue().getStatus()).isEqualTo(DeliveryItemStatus.PENDING.getCode());
    }

    @Test
    @DisplayName("状态迁移-非法")
    void changeItemStatusBad() {
        when(itemMapper.selectById(1L)).thenReturn(item(1L, "PENDING"));
        DeliveryItemStatusDTO dto = new DeliveryItemStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("XXX");
        assertThatThrownBy(() -> service.changeItemStatus(dto)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("状态迁移-PENDING->SUBMITTED")
    void changeItemStatusP2S() {
        when(itemMapper.selectById(1L)).thenReturn(item(1L, "PENDING"));
        DeliveryItemStatusDTO dto = new DeliveryItemStatusDTO();
        dto.setId(1L);
        dto.setTargetStatus("SUBMITTED");
        service.changeItemStatus(dto);
        verify(itemMapper).updateById(any(DeliveryItemDO.class));
    }

    @Test
    @DisplayName("TR 标记-无需 TR")
    void markTrCompletedNotRequired() {
        DeliveryItemDO i = item(1L, "PENDING");
        i.setTrRequired(0);
        when(itemMapper.selectById(1L)).thenReturn(i);
        assertThatThrownBy(() -> service.markTrCompleted(1L, 1)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("TR 标记-成功")
    void markTrCompletedOk() {
        DeliveryItemDO i = item(1L, "PENDING");
        i.setTrRequired(1);
        when(itemMapper.selectById(1L)).thenReturn(i);
        service.markTrCompleted(1L, 1);
        verify(itemMapper).updateTrCompleted(1L, 1);
    }

    @Test
    @DisplayName("删除-已验收 拒绝")
    void deleteItemAccepted() {
        when(itemMapper.selectById(1L)).thenReturn(item(1L, "ACCEPTED"));
        assertThatThrownBy(() -> service.deleteItem(1L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("按项目列出-空项目ID")
    void listItemsByInitiationNull() {
        assertThat(service.listItemsByInitiation(null)).isEmpty();
    }

    @Test
    @DisplayName("按项目+阶段列出")
    void listItemsByStage() {
        when(itemMapper.selectByStage(1L, "S1")).thenReturn(List.of(item(1L, "ACCEPTED")));
        assertThat(service.listItemsByStage(1L, "S1")).hasSize(1);
    }

    @Test
    @DisplayName("checkStageGate-阶段不合法 返回 fail")
    void checkStageGateBadStage() {
        StageGateValidator.GateCheckResult r = service.checkStageGate(1L, "XXX", "L3");
        assertThat(r).isNotNull();
        assertThat(r.passed()).isFalse();
    }

    private DeliveryItemCreateDTO validItem(String code) {
        DeliveryItemCreateDTO dto = new DeliveryItemCreateDTO();
        dto.setItemCode(code);
        dto.setInitiationId(1L);
        dto.setStage(DeliveryStage.CD1_KICKOFF.getCode());
        dto.setProjectType(ProjectType.FIXED_PRICE.getCode());
        return dto;
    }

    private DeliveryItemDO item(Long id, String status) {
        DeliveryItemDO i = new DeliveryItemDO();
        i.setId(id);
        i.setItemCode("D-" + id);
        i.setInitiationId(1L);
        i.setStatus(status);
        return i;
    }
}
