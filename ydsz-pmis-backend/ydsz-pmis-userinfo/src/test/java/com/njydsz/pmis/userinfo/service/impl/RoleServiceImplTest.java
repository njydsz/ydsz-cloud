package com.njydsz.pmis.userinfo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.RoleFormDTO;
import com.njydsz.pmis.userinfo.dto.RoleQueryDTO;
import com.njydsz.pmis.userinfo.entity.RoleDO;
import com.njydsz.pmis.userinfo.entity.RolePermissionDO;
import com.njydsz.pmis.userinfo.mapper.RoleMapper;
import com.njydsz.pmis.userinfo.mapper.RolePermissionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleServiceImpl 单元测试")
class RoleServiceImplTest {

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private RolePermissionMapper rolePermissionMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    @Nested
    @DisplayName("createRole 方法")
    class CreateRoleTest {

        @Test
        @DisplayName("创建角色 - 正常流程")
        void createRole_Success() {
            // Given
            RoleFormDTO dto = new RoleFormDTO();
            dto.setRoleCode("TEST_ROLE");
            dto.setRoleName("测试角色");
            dto.setPermissionIds(Arrays.asList(1L, 2L));

            when(roleMapper.selectByCode("TEST_ROLE")).thenReturn(null);
            doAnswer(invocation -> {
                RoleDO entity = invocation.getArgument(0);
                entity.setId(100L);
                return 1;
            }).when(roleMapper).insert(any(RoleDO.class));

            // When
            Long id = roleService.create(dto);

            // Then
            assertThat(id).isEqualTo(100L);
            verify(roleMapper).insert(any(RoleDO.class));
            verify(rolePermissionMapper, times(2)).insert(any(RolePermissionDO.class));
        }

        @Test
        @DisplayName("创建角色 - 角色编码已存在")
        void createRole_DuplicateCode() {
            // Given
            RoleFormDTO dto = new RoleFormDTO();
            dto.setRoleCode("EXISTING_ROLE");
            dto.setRoleName("已存在角色");

            RoleDO existing = new RoleDO();
            when(roleMapper.selectByCode("EXISTING_ROLE")).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> roleService.create(dto))
                    .isInstanceOf(BizException.class);
            verify(roleMapper, never()).insert(any(RoleDO.class));
        }

        @Test
        @DisplayName("创建角色 - 无权限ID列表时不创建权限关联")
        void createRole_NoPermissions() {
            // Given
            RoleFormDTO dto = new RoleFormDTO();
            dto.setRoleCode("NEW_ROLE");
            dto.setRoleName("新角色");
            dto.setPermissionIds(null);

            when(roleMapper.selectByCode("NEW_ROLE")).thenReturn(null);
            doAnswer(invocation -> {
                RoleDO entity = invocation.getArgument(0);
                entity.setId(101L);
                return 1;
            }).when(roleMapper).insert(any(RoleDO.class));

            // When
            Long id = roleService.create(dto);

            // Then
            assertThat(id).isEqualTo(101L);
            verify(roleMapper).insert(any(RoleDO.class));
            verify(rolePermissionMapper, never()).insert(any(RolePermissionDO.class));
        }
    }

    @Nested
    @DisplayName("updateRole 方法")
    class UpdateRoleTest {

        @Test
        @DisplayName("更新角色 - 正常流程")
        void updateRole_Success() {
            // Given
            RoleFormDTO dto = new RoleFormDTO();
            dto.setId(1L);
            dto.setRoleCode("UPDATED_ROLE");
            dto.setRoleName("更新后角色");

            RoleDO existing = new RoleDO();
            existing.setId(1L);
            existing.setRoleCode("OLD_ROLE");
            when(roleMapper.selectById(1L)).thenReturn(existing);
            when(roleMapper.updateById(any(RoleDO.class))).thenReturn(1);

            // When & Then
            assertThatCode(() -> roleService.update(dto)).doesNotThrowAnyException();
            verify(roleMapper).updateById(any(RoleDO.class));
        }

        @Test
        @DisplayName("更新角色 - ID为空")
        void updateRole_IdIsNull() {
            // Given
            RoleFormDTO dto = new RoleFormDTO();
            dto.setId(null);
            dto.setRoleCode("ROLE");

            // When & Then
            assertThatThrownBy(() -> roleService.update(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("更新角色 - 角色不存在")
        void updateRole_NotFound() {
            // Given
            RoleFormDTO dto = new RoleFormDTO();
            dto.setId(999L);
            dto.setRoleCode("ROLE");

            when(roleMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> roleService.update(dto))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("deleteRole 方法")
    class DeleteRoleTest {

        @Test
        @DisplayName("删除角色 - 正常流程")
        void deleteRole_Success() {
            // Given
            RoleDO role = new RoleDO();
            role.setId(1L);
            role.setRoleCode("NORMAL_ROLE");
            when(roleMapper.selectById(1L)).thenReturn(role);
            when(roleMapper.deleteById(1L)).thenReturn(1);
            when(rolePermissionMapper.delete(any())).thenReturn(1);

            // When & Then
            assertThatCode(() -> roleService.delete(1L)).doesNotThrowAnyException();
            verify(roleMapper).deleteById(1L);
            verify(rolePermissionMapper).delete(any());
        }

        @Test
        @DisplayName("删除角色 - 角色不存在")
        void deleteRole_NotFound() {
            // Given
            when(roleMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> roleService.delete(999L))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("删除角色 - SUPER_ADMIN不允许删除")
        void deleteRole_SuperAdminForbidden() {
            // Given
            RoleDO role = new RoleDO();
            role.setId(1L);
            role.setRoleCode("SUPER_ADMIN");
            when(roleMapper.selectById(1L)).thenReturn(role);

            // When & Then
            assertThatThrownBy(() -> roleService.delete(1L))
                    .isInstanceOf(BizException.class);
            verify(roleMapper, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("assignPermissions 方法")
    class AssignPermissionsTest {

        @Test
        @DisplayName("分配权限 - 正常流程")
        void assignPermissions_Success() {
            // Given
            Long roleId = 1L;
            List<Long> permissionIds = Arrays.asList(10L, 20L, 30L);
            when(rolePermissionMapper.delete(any())).thenReturn(1);

            // When
            roleService.assignPermissions(roleId, permissionIds);

            // Then
            verify(rolePermissionMapper).delete(any());
            verify(rolePermissionMapper, times(3)).insert(any(RolePermissionDO.class));
        }

        @Test
        @DisplayName("分配权限 - 空权限列表时只清除旧关联")
        void assignPermissions_EmptyList() {
            // Given
            Long roleId = 1L;
            List<Long> permissionIds = List.of();
            when(rolePermissionMapper.delete(any())).thenReturn(1);

            // When
            roleService.assignPermissions(roleId, permissionIds);

            // Then
            verify(rolePermissionMapper).delete(any());
            verify(rolePermissionMapper, never()).insert(any(RolePermissionDO.class));
        }

        @Test
        @DisplayName("分配权限 - null权限列表时只清除旧关联")
        void assignPermissions_NullList() {
            // Given
            Long roleId = 1L;
            when(rolePermissionMapper.delete(any())).thenReturn(1);

            // When
            roleService.assignPermissions(roleId, null);

            // Then
            verify(rolePermissionMapper).delete(any());
            verify(rolePermissionMapper, never()).insert(any(RolePermissionDO.class));
        }
    }

    @Nested
    @DisplayName("page 方法")
    class PageTest {

        @Test
        @DisplayName("分页查询 - 正常流程")
        @SuppressWarnings("unchecked")
        void page_Success() {
            // Given
            RoleQueryDTO query = new RoleQueryDTO();
            query.setPage(1);
            query.setSize(10);
            when(roleMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            // When
            Page<RoleDO> result = roleService.page(query);

            // Then
            assertThat(result).isNotNull();
            verify(roleMapper).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("getById 方法")
    class GetByIdTest {

        @Test
        @DisplayName("根据ID查询 - 正常流程")
        void getById_Success() {
            // Given
            RoleDO role = new RoleDO();
            role.setId(1L);
            role.setRoleCode("TEST_ROLE");
            when(roleMapper.selectById(1L)).thenReturn(role);

            // When
            RoleDO result = roleService.getById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("根据ID查询 - 角色不存在")
        void getById_NotFound() {
            // Given
            when(roleMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> roleService.getById(999L))
                    .isInstanceOf(BizException.class);
        }
    }
}
