package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.userinfo.dto.EmployeeTagCreateDTO;
import com.njydsz.pmis.userinfo.entity.EmployeeTagDO;
import com.njydsz.pmis.userinfo.mapper.EmployeeTagMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("人员标签服务测试")
class EmployeeTagServiceImplTest {

    @Mock
    private EmployeeTagMapper tagMapper;

    @InjectMocks
    private EmployeeTagServiceImpl employeeTagService;

    @Test
    @DisplayName("添加人员标签成功")
    void add_shouldInsertTag() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::getTenantId).thenReturn(1L);

            EmployeeTagCreateDTO dto = new EmployeeTagCreateDTO();
            dto.setEmployeeId(1L);
            dto.setTagType("SKILL");
            dto.setTagCode("JAVA");
            dto.setTagName("Java");
            dto.setProficiency(4);

            doAnswer(invocation -> {
                EmployeeTagDO entity = invocation.getArgument(0);
                entity.setId(600L);
                return 1;
            }).when(tagMapper).insert(any(EmployeeTagDO.class));

            Long id = employeeTagService.add(dto);
            assertNotNull(id);
            assertEquals(600L, id);
            verify(tagMapper).insert(any(EmployeeTagDO.class));
        }
    }

    @Test
    @DisplayName("添加标签时熟练度超出范围抛出异常")
    void add_invalidProficiency_shouldThrowException() {
        EmployeeTagCreateDTO dto = new EmployeeTagCreateDTO();
        dto.setEmployeeId(1L);
        dto.setTagType("SKILL");
        dto.setTagCode("JAVA");
        dto.setTagName("Java");
        dto.setProficiency(6);

        BizException ex = assertThrows(BizException.class, () -> employeeTagService.add(dto));
        assertEquals(10001, ex.getCode());
    }

    @Test
    @DisplayName("删除人员标签")
    void remove_shouldDeleteTag() {
        when(tagMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> employeeTagService.remove(1L));
        verify(tagMapper).deleteById(1L);
    }

    @Test
    @DisplayName("根据员工ID查询标签列表")
    void listByEmployee_shouldReturnTagList() {
        EmployeeTagDO tag = new EmployeeTagDO();
        tag.setId(1L);
        tag.setEmployeeId(1L);
        tag.setTagType("SKILL");
        tag.setTagCode("JAVA");

        when(tagMapper.selectByEmployee(1L)).thenReturn(List.of(tag));

        List<EmployeeTagDO> result = employeeTagService.listByEmployee(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("JAVA", result.get(0).getTagCode());
    }

    @Test
    @DisplayName("根据标签类型和编码查询候选人")
    void findCandidates_shouldReturnEmployeeList() {
        EmployeeTagDO tag = new EmployeeTagDO();
        tag.setId(1L);
        tag.setEmployeeId(1L);
        tag.setTagType("SKILL");
        tag.setTagCode("JAVA");

        when(tagMapper.selectByTag("SKILL", "JAVA")).thenReturn(List.of(tag));

        List<EmployeeTagDO> result = employeeTagService.findCandidates("SKILL", "JAVA");
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("批量替换员工标签")
    void replaceByEmployee_shouldReplaceAllTags() {
        try (MockedStatic<TenantContext> tenantContext = mockStatic(TenantContext.class)) {
            tenantContext.when(TenantContext::getTenantId).thenReturn(1L);

            EmployeeTagCreateDTO dto = new EmployeeTagCreateDTO();
            dto.setEmployeeId(1L);
            dto.setTagType("SKILL");
            dto.setTagCode("PYTHON");
            dto.setTagName("Python");
            dto.setProficiency(3);

            doAnswer(invocation -> {
                EmployeeTagDO entity = invocation.getArgument(0);
                entity.setId(601L);
                return 1;
            }).when(tagMapper).insert(any(EmployeeTagDO.class));

            assertDoesNotThrow(() -> employeeTagService.replaceByEmployee(1L, List.of(dto)));
            verify(tagMapper).deleteByEmployee(1L);
            verify(tagMapper).insert(any(EmployeeTagDO.class));
        }
    }
}