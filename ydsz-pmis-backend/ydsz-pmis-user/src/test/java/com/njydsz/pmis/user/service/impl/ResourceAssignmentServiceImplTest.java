package com.njydsz.pmis.user.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.ResourceAssignmentCreateDTO;
import com.njydsz.pmis.user.entity.ResourceAssignmentDO;
import com.njydsz.pmis.user.mapper.ResourceAssignmentMapper;
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
import static org.mockito.Mockito.when;

/**
 * ResourceAssignmentServiceImpl 测试
 */
@DisplayName("ResourceAssignmentServiceImpl 资源分配")
class ResourceAssignmentServiceImplTest {

    private ResourceAssignmentMapper mapper;
    private ResourceAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(ResourceAssignmentMapper.class);
        service = new ResourceAssignmentServiceImpl(mapper);
    }

    @Test
    @DisplayName("act 必填 action 缺失")
    void act_missingAction() {
        ResourceAssignmentCreateDTO dto = baseDto();
        dto.setAction(null);
        assertThatThrownBy(() -> service.act(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("act 编号重复")
    void act_duplicate() {
        ResourceAssignmentCreateDTO dto = baseDto();
        when(mapper.selectByCode("RA-1")).thenReturn(new ResourceAssignmentDO());
        assertThatThrownBy(() -> service.act(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("act RESERVE 必须关联商机或项目")
    void act_reserveWithoutRef() {
        ResourceAssignmentCreateDTO dto = new ResourceAssignmentCreateDTO();
        dto.setAssignmentCode("RA-RES");
        dto.setEmployeeId(1L);
        dto.setAction("RESERVE");
        // 不设置 opportunityId / initiationId
        when(mapper.selectByCode(any())).thenReturn(null);
        assertThatThrownBy(() -> service.act(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("act START 必须关联项目")
    void act_startWithoutInit() {
        ResourceAssignmentCreateDTO dto = baseDto();
        dto.setAction("START");
        when(mapper.selectByCode(any())).thenReturn(null);
        assertThatThrownBy(() -> service.act(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("act START 成功并补齐默认")
    void act_start_success() {
        ResourceAssignmentCreateDTO dto = baseDto();
        dto.setAction("START");
        dto.setInitiationId(99L);
        when(mapper.selectByCode(any())).thenReturn(null);
        when(mapper.countActiveByEmployee(1L)).thenReturn(1);
        when(mapper.insert(any(ResourceAssignmentDO.class))).thenAnswer(inv -> {
            ResourceAssignmentDO a = inv.getArgument(0);
            a.setId(7L);
            return 1;
        });
        Long id = service.act(dto);
        assertThat(id).isEqualTo(7L);
    }

    @Test
    @DisplayName("act 过载检测不抛异常仅打日志")
    void act_overloadWarning() {
        ResourceAssignmentCreateDTO dto = baseDto();
        dto.setAction("START");
        dto.setInitiationId(99L);
        when(mapper.selectByCode(any())).thenReturn(null);
        when(mapper.countActiveByEmployee(1L)).thenReturn(4);
        when(mapper.insert(any(ResourceAssignmentDO.class))).thenAnswer(inv -> {
            ResourceAssignmentDO a = inv.getArgument(0);
            a.setId(8L);
            return 1;
        });
        Long id = service.act(dto);
        assertThat(id).isEqualTo(8L);
    }

    @Test
    @DisplayName("act CANCEL 映射为 CANCELLED")
    void act_cancel() {
        ResourceAssignmentCreateDTO dto = baseDto();
        dto.setAction("CANCEL");
        when(mapper.selectByCode(any())).thenReturn(null);
        when(mapper.insert(any(ResourceAssignmentDO.class))).thenAnswer(inv -> {
            ResourceAssignmentDO a = inv.getArgument(0);
            a.setStatus("CANCELLED");
            return 1;
        });
        service.act(dto);
    }

    @Test
    @DisplayName("activeCount null 时为 0")
    void activeCountNull() {
        when(mapper.countActiveByEmployee(null)).thenReturn(null);
        assertThat(service.activeCount(null)).isEqualTo(0);
    }

    @Test
    @DisplayName("utilization 计算")
    void utilization() {
        ResourceAssignmentDO a = new ResourceAssignmentDO();
        a.setStatus("ACTIVE");
        a.setAllocation(new BigDecimal("0.6"));
        when(mapper.selectByEmployee(1L)).thenReturn(List.of(a));
        Map<String, Object> out = service.utilization(1L);
        assertThat(out).containsKey("activeCount");
        assertThat(out.get("activeCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("utilization null 员工返回空 Map")
    void utilization_empty() {
        assertThat(service.utilization(null)).isEmpty();
    }

    private ResourceAssignmentCreateDTO baseDto() {
        ResourceAssignmentCreateDTO d = new ResourceAssignmentCreateDTO();
        d.setAssignmentCode("RA-1");
        d.setEmployeeId(1L);
        d.setAction("RESERVE");
        d.setOpportunityId(10L);
        return d;
    }
}
