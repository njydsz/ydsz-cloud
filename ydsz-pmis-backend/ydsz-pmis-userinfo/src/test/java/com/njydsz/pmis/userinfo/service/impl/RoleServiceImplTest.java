package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.RoleFormDTO;
import com.njydsz.pmis.userinfo.dto.RoleQueryDTO;
import com.njydsz.pmis.userinfo.entity.RoleDO;
import com.njydsz.pmis.userinfo.entity.RolePermissionDO;
import com.njydsz.pmis.userinfo.mapper.RoleMapper;
import com.njydsz.pmis.userinfo.mapper.RolePermissionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("角色服务测试")
class RoleServiceImplTest {

    @Mock
    private RoleMapper roleMapper;
    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    @Test
    @DisplayName("分页查询角色")
    void page_shouldReturnPagedResult() {
        RoleQueryDTO query = new RoleQueryDTO();
        query.setPage(1);
        query.setSize(10);

        Page<RoleDO> mockPage = new Page<>(1, 10);
        RoleDO role = new RoleDO();
        role.setId(1L);
        role.setRoleCode("ADMIN");
        role.setRoleName("管理员");
        mockPage.setRecords(List.of(role));
        mockPage.setTotal(1);

        when(roleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<RoleDO> result = roleService.page(query);
        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("查询所有启用的角色")
    void listAllEnabled_shouldReturnRoleList() {
        RoleDO role = new RoleDO();
        role.setId(1L);
        role.setRoleCode("ADMIN");
        role.setRoleName("管理员");
        role.setStatus("ENABLED");

        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(role));

        List<RoleDO> result = roleService.listAllEnabled();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ADMIN", result.get(0).getRoleCode());
    }

    @Test
    @DisplayName("根据ID查询角色")
    void getById_shouldReturnRole() {
        RoleDO role = new RoleDO();
        role.setId(1L);
        role.setRoleCode("ADMIN");
        role.setRoleName("管理员");

        when(roleMapper.selectById(1L)).thenReturn(role);

        RoleDO result = roleService.getById(1L);
        assertNotNull(result);
        assertEquals("ADMIN", result.getRoleCode());
    }

    @Test
    @DisplayName("根据ID查询不存在的角色时抛出异常")
    void getById_notFound_shouldThrowException() {
        when(roleMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> roleService.getById(999L));
        assertEquals(10101, ex.getCode());
    }

    @Test
    @DisplayName("根据用户ID查询角色列表")
    void listByUserId_shouldReturnRoleList() {
        RoleDO role = new RoleDO();
        role.setId(1L);
        role.setRoleCode("ADMIN");

        when(roleMapper.selectByUserId(1L)).thenReturn(List.of(role));

        List<RoleDO> result = roleService.listByUserId(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ADMIN", result.get(0).getRoleCode());
    }

    @Test
    @DisplayName("创建角色成功")
    void create_shouldInsertRole() {
        RoleFormDTO dto = new RoleFormDTO();
        dto.setRoleCode("NEW_ROLE");
        dto.setRoleName("新角色");
        dto.setPermissionIds(Collections.emptyList());

        when(roleMapper.selectByCode("NEW_ROLE")).thenReturn(null);
        doAnswer(invocation -> {
            RoleDO entity = invocation.getArgument(0);
            entity.setId(200L);
            return 1;
        }).when(roleMapper).insert(any(RoleDO.class));

        Long id = roleService.create(dto);
        assertNotNull(id);
        assertEquals(200L, id);
        verify(roleMapper).insert(any(RoleDO.class));
    }

    @Test
    @DisplayName("删除SUPER_ADMIN角色时抛出异常")
    void delete_superAdmin_shouldThrowException() {
        RoleDO superAdmin = new RoleDO();
        superAdmin.setId(1L);
        superAdmin.setRoleCode("SUPER_ADMIN");

        when(roleMapper.selectById(1L)).thenReturn(superAdmin);

        BizException ex = assertThrows(BizException.class, () -> roleService.delete(1L));
        assertEquals(10001, ex.getCode());
    }

    @Test
    @DisplayName("分配权限给角色")
    void assignPermissions_shouldDeleteAndInsert() {
        List<Long> permIds = List.of(1L, 2L);

        roleService.assignPermissions(1L, permIds);

        verify(rolePermissionMapper).delete(any(LambdaQueryWrapper.class));
        verify(rolePermissionMapper, times(2)).insert(any(RolePermissionDO.class));
    }
}