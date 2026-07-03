package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.DepartmentFormDTO;
import com.njydsz.pmis.userinfo.entity.DepartmentDO;
import com.njydsz.pmis.userinfo.mapper.DepartmentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("部门服务测试")
class DepartmentServiceImplTest {

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    @Test
    @DisplayName("查询部门树")
    void tree_shouldReturnDepartmentTree() {
        DepartmentDO dept = new DepartmentDO();
        dept.setId(1L);
        dept.setDeptCode("DEPT001");
        dept.setDeptName("技术部");
        dept.setParentId(0L);
        dept.setDeptPath("/1");

        when(departmentMapper.selectAllEnabled()).thenReturn(List.of(dept));

        var result = departmentService.tree();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("技术部", result.get(0).getDepartment().getDeptName());
    }

    @Test
    @DisplayName("查询所有启用的部门")
    void listAllEnabled_shouldReturnDepartmentList() {
        DepartmentDO dept = new DepartmentDO();
        dept.setId(1L);
        dept.setDeptCode("DEPT001");
        dept.setDeptName("技术部");

        when(departmentMapper.selectAllEnabled()).thenReturn(List.of(dept));

        List<DepartmentDO> result = departmentService.listAllEnabled();
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("根据ID查询部门")
    void getById_shouldReturnDepartment() {
        DepartmentDO dept = new DepartmentDO();
        dept.setId(1L);
        dept.setDeptCode("DEPT001");
        dept.setDeptName("技术部");

        when(departmentMapper.selectById(1L)).thenReturn(dept);

        DepartmentDO result = departmentService.getById(1L);
        assertNotNull(result);
        assertEquals("DEPT001", result.getDeptCode());
    }

    @Test
    @DisplayName("根据ID查询不存在的部门时抛出异常")
    void getById_notFound_shouldThrowException() {
        when(departmentMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> departmentService.getById(999L));
        assertEquals(30101, ex.getCode());
    }

    @Test
    @DisplayName("创建部门成功")
    void create_shouldInsertDepartment() {
        DepartmentFormDTO dto = new DepartmentFormDTO();
        dto.setDeptCode("NEWDEPT");
        dto.setDeptName("新部门");
        dto.setParentId(0L);

        when(departmentMapper.selectByCode("NEWDEPT")).thenReturn(null);
        doAnswer(invocation -> {
            DepartmentDO entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(departmentMapper).insert(any(DepartmentDO.class));
        when(departmentMapper.updateById(any(DepartmentDO.class))).thenReturn(1);

        Long id = departmentService.create(dto);
        assertNotNull(id);
        assertEquals(100L, id);
        verify(departmentMapper).insert(any(DepartmentDO.class));
        verify(departmentMapper).updateById(any(DepartmentDO.class));
    }

    @Test
    @DisplayName("创建部门时编码重复抛出异常")
    void create_duplicateCode_shouldThrowException() {
        DepartmentDO existing = new DepartmentDO();
        existing.setId(1L);
        existing.setDeptCode("DUP001");

        when(departmentMapper.selectByCode("DUP001")).thenReturn(existing);

        DepartmentFormDTO dto = new DepartmentFormDTO();
        dto.setDeptCode("DUP001");
        dto.setDeptName("重复部门");

        BizException ex = assertThrows(BizException.class, () -> departmentService.create(dto));
        assertEquals(10102, ex.getCode());
    }

    @Test
    @DisplayName("删除有子部门的部门时抛出异常")
    void delete_withChildren_shouldThrowException() {
        DepartmentDO dept = new DepartmentDO();
        dept.setId(1L);
        dept.setDeptCode("DEPT001");

        DepartmentDO child = new DepartmentDO();
        child.setId(2L);

        when(departmentMapper.selectById(1L)).thenReturn(dept);
        when(departmentMapper.selectByParentId(1L)).thenReturn(List.of(child));

        BizException ex = assertThrows(BizException.class, () -> departmentService.delete(1L));
        assertEquals(10001, ex.getCode());
    }

    @Test
    @DisplayName("删除不存在的部门时抛出异常")
    void delete_notFound_shouldThrowException() {
        when(departmentMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> departmentService.delete(999L));
        assertEquals(30101, ex.getCode());
    }
}