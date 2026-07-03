package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.entity.PermissionDO;
import com.njydsz.pmis.userinfo.mapper.PermissionMapper;
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
@DisplayName("权限服务测试")
class PermissionServiceImplTest {

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @Test
    @DisplayName("根据角色ID查询权限列表")
    void listByRoleId_shouldReturnPermissionList() {
        PermissionDO perm = new PermissionDO();
        perm.setId(1L);
        perm.setPermCode("system:user:list");
        perm.setPermName("用户列表");

        when(permissionMapper.selectByRoleId(1L)).thenReturn(List.of(perm));

        List<PermissionDO> result = permissionService.listByRoleId(1L);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("system:user:list", result.get(0).getPermCode());
    }

    @Test
    @DisplayName("根据角色ID查询权限列表为空")
    void listByRoleId_empty_shouldReturnEmptyList() {
        when(permissionMapper.selectByRoleId(999L)).thenReturn(List.of());

        List<PermissionDO> result = permissionService.listByRoleId(999L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("根据ID查询权限")
    void getById_shouldReturnPermission() {
        PermissionDO perm = new PermissionDO();
        perm.setId(1L);
        perm.setPermCode("system:user:list");
        perm.setPermName("用户列表");

        when(permissionMapper.selectById(1L)).thenReturn(perm);

        PermissionDO result = permissionService.getById(1L);
        assertNotNull(result);
        assertEquals("system:user:list", result.getPermCode());
    }

    @Test
    @DisplayName("根据ID查询不存在的权限时抛出异常")
    void getById_notFound_shouldThrowException() {
        when(permissionMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> permissionService.getById(999L));
        assertEquals(10101, ex.getCode());
    }

    @Test
    @DisplayName("根据用户ID查询权限编码列表")
    void listPermCodesByUserId_shouldReturnPermCodeList() {
        when(permissionMapper.selectPermCodesByUserId(1L))
                .thenReturn(List.of("system:user:list", "system:user:create"));

        List<String> result = permissionService.listPermCodesByUserId(1L);
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("system:user:list"));
    }

    @Test
    @DisplayName("查询所有启用的权限")
    void listAllEnabled_shouldReturnPermissionList() {
        PermissionDO perm = new PermissionDO();
        perm.setId(1L);
        perm.setPermCode("system:user:list");
        perm.setStatus("ENABLED");

        when(permissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(perm));

        List<PermissionDO> result = permissionService.listAllEnabled();
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}