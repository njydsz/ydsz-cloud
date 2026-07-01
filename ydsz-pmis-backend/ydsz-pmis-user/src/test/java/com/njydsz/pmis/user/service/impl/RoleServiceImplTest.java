package com.njydsz.pmis.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.RoleFormDTO;
import com.njydsz.pmis.user.entity.RoleDO;
import com.njydsz.pmis.user.entity.RolePermissionDO;
import com.njydsz.pmis.user.mapper.RoleMapper;
import com.njydsz.pmis.user.mapper.RolePermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RoleServiceImpl 单元测试
 */
@SuppressWarnings("unchecked")
@DisplayName("RoleServiceImpl 角色服务测试")
class RoleServiceImplTest {

    private RoleMapper roleMapper;
    private RolePermissionMapper rolePermissionMapper;
    private RoleServiceImpl service;

    @BeforeEach
    void setUp() {
        roleMapper = mock(RoleMapper.class);
        rolePermissionMapper = mock(RolePermissionMapper.class);
        service = new RoleServiceImpl(roleMapper, rolePermissionMapper);
    }

    @Test
    @DisplayName("create 角色编码重复应抛 DUPLICATE_KEY")
    void create_duplicate() {
        when(roleMapper.selectByCode("ADMIN")).thenReturn(role(1L, "ADMIN"));
        RoleFormDTO dto = form("ADMIN", "管理员", null);
        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create 应插入并分配权限")
    void create_withPerms() {
        when(roleMapper.selectByCode("PM")).thenReturn(null);
        when(roleMapper.insert(any(RoleDO.class))).thenAnswer(inv -> {
            RoleDO r = inv.getArgument(0);
            r.setId(100L);
            return 1;
        });

        RoleFormDTO dto = form("PM", "项目经理", List.of(1L, 2L));
        Long id = service.create(dto);
        assertThat(id).isEqualTo(100L);
        verify(rolePermissionMapper, times(1)).delete(any(LambdaQueryWrapper.class));
        verify(rolePermissionMapper, times(2)).insert(any(RolePermissionDO.class));
    }

    @Test
    @DisplayName("update 不存在应抛 NOT_FOUND")
    void update_notFound() {
        when(roleMapper.selectById(99L)).thenReturn(null);
        RoleFormDTO dto = form("X", "X", null);
        dto.setId(99L);
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("delete 内置 SUPER_ADMIN 应拒绝")
    void delete_superAdmin() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, "SUPER_ADMIN"));
        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("delete 应级联清除角色权限")
    void delete_cascade() {
        when(roleMapper.selectById(2L)).thenReturn(role(2L, "PM"));
        service.delete(2L);
        verify(roleMapper).deleteById(2L);
        verify(rolePermissionMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("assignPermissions 空列表应仅删除")
    void assign_empty() {
        service.assignPermissions(1L, List.of());
        verify(rolePermissionMapper, times(1)).delete(any(LambdaQueryWrapper.class));
        verify(rolePermissionMapper, never()).insert(any(RolePermissionDO.class));
    }

    @Test
    @DisplayName("assignPermissions 多次应先删后插")
    void assign_replace() {
        service.assignPermissions(1L, List.of(10L, 20L, 30L));
        verify(rolePermissionMapper, times(3)).insert(any(RolePermissionDO.class));
    }

    private RoleFormDTO form(String code, String name, List<Long> perms) {
        RoleFormDTO dto = new RoleFormDTO();
        dto.setRoleCode(code);
        dto.setRoleName(name);
        dto.setDataScope("SELF");
        dto.setStatus("ENABLED");
        dto.setPermissionIds(perms);
        return dto;
    }

    private RoleDO role(Long id, String code) {
        RoleDO r = new RoleDO();
        r.setId(id);
        r.setRoleCode(code);
        r.setRoleName(code);
        r.setDataScope("SELF");
        r.setStatus("ENABLED");
        return r;
    }
}
