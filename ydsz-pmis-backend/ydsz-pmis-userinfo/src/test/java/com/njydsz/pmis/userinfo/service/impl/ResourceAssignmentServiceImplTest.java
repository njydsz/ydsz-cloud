package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.userinfo.dto.ResourceAssignmentCreateDTO;
import com.njydsz.pmis.userinfo.entity.ResourceAssignmentDO;
import com.njydsz.pmis.userinfo.mapper.ResourceAssignmentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("资源分配服务测试")
class ResourceAssignmentServiceImplTest {

    @Mock
    private ResourceAssignmentMapper assignmentMapper;

    @InjectMocks
    private ResourceAssignmentServiceImpl resourceAssignmentService;

    @Test
    @DisplayName("RESERVE动作创建资源分配")
    void act_reserve_shouldCreateAssignment() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::getTenantId).thenReturn(1L);

            ResourceAssignmentCreateDTO dto = new ResourceAssignmentCreateDTO();
            dto.setAssignmentCode("ASSIGN001");
            dto.setEmployeeId(1L);
            dto.setAction("RESERVE");
            dto.setOpportunityId(100L);

            when(assignmentMapper.selectByCode("ASSIGN001")).thenReturn(null);
            when(assignmentMapper.countActiveByEmployee(1L)).thenReturn(0);
            doAnswer(invocation -> {
                ResourceAssignmentDO entity = invocation.getArgument(0);
                entity.setId(400L);
                return 1;
            }).when(assignmentMapper).insert(any(ResourceAssignmentDO.class));

            Long id = resourceAssignmentService.act(dto);
            assertNotNull(id);
            assertEquals(400L, id);
            verify(assignmentMapper).insert(any(ResourceAssignmentDO.class));
        }
    }

    @Test
    @DisplayName("RESERVE动作缺少商机ID和项目ID时抛出异常")
    void act_reserve_missingBoth_shouldThrowException() {
        ResourceAssignmentCreateDTO dto = new ResourceAssignmentCreateDTO();
        dto.setAssignmentCode("ASSIGN001");
        dto.setEmployeeId(1L);
        dto.setAction("RESERVE");

        when(assignmentMapper.selectByCode("ASSIGN001")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> resourceAssignmentService.act(dto));
        assertEquals(10001, ex.getCode());
    }

    @Test
    @DisplayName("根据ID查询资源分配")
    void getById_shouldReturnAssignment() {
        ResourceAssignmentDO assignment = new ResourceAssignmentDO();
        assignment.setId(1L);
        assignment.setAssignmentCode("ASSIGN001");
        assignment.setEmployeeId(1L);

        when(assignmentMapper.selectById(1L)).thenReturn(assignment);

        ResourceAssignmentDO result = resourceAssignmentService.getById(1L);
        assertNotNull(result);
        assertEquals("ASSIGN001", result.getAssignmentCode());
    }

    @Test
    @DisplayName("根据ID查询不存在的资源分配时抛出异常")
    void getById_notFound_shouldThrowException() {
        when(assignmentMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> resourceAssignmentService.getById(999L));
        assertEquals(10101, ex.getCode());
    }

    @Test
    @DisplayName("根据员工ID查询资源分配列表")
    void listByEmployee_shouldReturnAssignmentList() {
        ResourceAssignmentDO assignment = new ResourceAssignmentDO();
        assignment.setId(1L);
        assignment.setAssignmentCode("ASSIGN001");
        assignment.setEmployeeId(1L);

        when(assignmentMapper.selectByEmployee(1L)).thenReturn(List.of(assignment));

        List<ResourceAssignmentDO> result = resourceAssignmentService.listByEmployee(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("计算员工利用率")
    void utilization_shouldReturnUtilizationMap() {
        when(assignmentMapper.selectByEmployee(1L)).thenReturn(List.of());

        Map<String, Object> result = resourceAssignmentService.utilization(1L);
        assertNotNull(result);
        assertTrue(result.containsKey("activeCount"));
        assertTrue(result.containsKey("totalAllocation"));
    }

    @Test
    @DisplayName("分页查询资源分配")
    void page_shouldReturnPagedResult() {
        Page<ResourceAssignmentDO> mockPage = new Page<>(1, 10);
        ResourceAssignmentDO assignment = new ResourceAssignmentDO();
        assignment.setId(1L);
        assignment.setAssignmentCode("ASSIGN001");
        mockPage.setRecords(List.of(assignment));
        mockPage.setTotal(1);

        when(assignmentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<ResourceAssignmentDO> result = resourceAssignmentService.page(1, 10, null, null, null);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }
}