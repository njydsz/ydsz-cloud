package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.PermissionFormDTO;
import com.njydsz.pmis.userinfo.entity.PermissionDO;
import com.njydsz.pmis.userinfo.mapper.PermissionMapper;
import com.njydsz.pmis.userinfo.vo.MenuTreeVO;
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
@DisplayName("PermissionServiceImpl 单元测试")
class PermissionServiceImplTest {

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @Nested
    @DisplayName("createPermission 方法")
    class CreatePermissionTest {

        @Test
        @DisplayName("创建权限 - 正常流程")
        void createPermission_Success() {
            // Given
            PermissionFormDTO dto = new PermissionFormDTO();
            dto.setPermCode("system:user:create");
            dto.setPermName("创建用户");
            dto.setPermType("BUTTON");

            when(permissionMapper.selectByCode("system:user:create")).thenReturn(null);
            doAnswer(invocation -> {
                PermissionDO entity = invocation.getArgument(0);
                entity.setId(100L);
                return 1;
            }).when(permissionMapper).insert(any(PermissionDO.class));

            // When
            Long id = permissionService.create(dto);

            // Then
            assertThat(id).isEqualTo(100L);
            verify(permissionMapper).insert(any(PermissionDO.class));
        }

        @Test
        @DisplayName("创建权限 - 权限编码已存在")
        void createPermission_DuplicateCode() {
            // Given
            PermissionFormDTO dto = new PermissionFormDTO();
            dto.setPermCode("system:user:existing");
            dto.setPermName("已存在权限");
            dto.setPermType("BUTTON");

            PermissionDO existing = new PermissionDO();
            when(permissionMapper.selectByCode("system:user:existing")).thenReturn(existing);

            // When & Then
            assertThatThrownBy(() -> permissionService.create(dto))
                    .isInstanceOf(BizException.class);
            verify(permissionMapper, never()).insert(any(PermissionDO.class));
        }

        @Test
        @DisplayName("创建权限 - 默认值设置")
        void createPermission_DefaultValues() {
            // Given
            PermissionFormDTO dto = new PermissionFormDTO();
            dto.setPermCode("system:test:api");
            dto.setPermName("测试API");
            dto.setPermType("API");
            // 不设置 status, visible, parentId

            when(permissionMapper.selectByCode("system:test:api")).thenReturn(null);
            doAnswer(invocation -> {
                PermissionDO entity = invocation.getArgument(0);
                entity.setId(101L);
                // 验证默认值
                assertThat(entity.getStatus()).isEqualTo("ENABLED");
                assertThat(entity.getVisible()).isEqualTo(1);
                assertThat(entity.getParentId()).isEqualTo(0L);
                return 1;
            }).when(permissionMapper).insert(any(PermissionDO.class));

            // When
            Long id = permissionService.create(dto);

            // Then
            assertThat(id).isEqualTo(101L);
        }
    }

    @Nested
    @DisplayName("getTree / listAllMenuTree 方法")
    class GetTreeTest {

        @Test
        @DisplayName("获取菜单树 - 正常流程")
        void getTree_Success() {
            // Given
            PermissionDO parent = new PermissionDO();
            parent.setId(1L);
            parent.setParentId(0L);
            parent.setPermCode("system");
            parent.setPermName("系统管理");
            parent.setPermType("MENU");
            parent.setSortOrder(1);

            PermissionDO child = new PermissionDO();
            child.setId(2L);
            child.setParentId(1L);
            child.setPermCode("system:user");
            child.setPermName("用户管理");
            child.setPermType("MENU");
            child.setSortOrder(1);

            when(permissionMapper.selectList(any())).thenReturn(Arrays.asList(parent, child));

            // When
            List<MenuTreeVO> result = permissionService.listAllMenuTree();

            // Then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getChildren()).hasSize(1);
        }

        @Test
        @DisplayName("获取菜单树 - 空列表")
        void getTree_EmptyList() {
            // Given
            when(permissionMapper.selectList(any())).thenReturn(List.of());

            // When
            List<MenuTreeVO> result = permissionService.listAllMenuTree();

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("获取菜单树 - 父节点未授权时作为根处理")
        void getTree_OrphanNodeAsRoot() {
            // Given
            PermissionDO orphan = new PermissionDO();
            orphan.setId(10L);
            orphan.setParentId(999L); // 父节点不存在
            orphan.setPermCode("orphan:menu");
            orphan.setPermName("孤儿菜单");
            orphan.setPermType("MENU");
            orphan.setSortOrder(1);

            when(permissionMapper.selectList(any())).thenReturn(List.of(orphan));

            // When
            List<MenuTreeVO> result = permissionService.listAllMenuTree();

            // Then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("assignToRole / listByRoleId 方法")
    class AssignToRoleTest {

        @Test
        @DisplayName("查询角色权限列表 - 正常流程")
        void listByRoleId_Success() {
            // Given
            Long roleId = 1L;
            PermissionDO perm1 = new PermissionDO();
            perm1.setId(10L);
            PermissionDO perm2 = new PermissionDO();
            perm2.setId(20L);
            when(permissionMapper.selectByRoleId(1L)).thenReturn(Arrays.asList(perm1, perm2));

            // When
            List<PermissionDO> result = permissionService.listByRoleId(roleId);

            // Then
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("查询角色权限列表 - 无权限时返回空列表")
        void listByRoleId_Empty() {
            // Given
            when(permissionMapper.selectByRoleId(999L)).thenReturn(List.of());

            // When
            List<PermissionDO> result = permissionService.listByRoleId(999L);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("update 方法")
    class UpdateTest {

        @Test
        @DisplayName("更新权限 - 正常流程")
        void update_Success() {
            // Given
            PermissionFormDTO dto = new PermissionFormDTO();
            dto.setId(1L);
            dto.setPermCode("system:user:update");
            dto.setPermName("更新用户");
            dto.setPermType("BUTTON");

            PermissionDO existing = new PermissionDO();
            existing.setId(1L);
            when(permissionMapper.selectById(1L)).thenReturn(existing);
            when(permissionMapper.updateById(any(PermissionDO.class))).thenReturn(1);

            // When & Then
            assertThatCode(() -> permissionService.update(dto)).doesNotThrowAnyException();
            verify(permissionMapper).updateById(any(PermissionDO.class));
        }

        @Test
        @DisplayName("更新权限 - ID为空")
        void update_IdIsNull() {
            // Given
            PermissionFormDTO dto = new PermissionFormDTO();
            dto.setId(null);
            dto.setPermCode("code");

            // When & Then
            assertThatThrownBy(() -> permissionService.update(dto))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("更新权限 - 权限不存在")
        void update_NotFound() {
            // Given
            PermissionFormDTO dto = new PermissionFormDTO();
            dto.setId(999L);
            dto.setPermCode("code");

            when(permissionMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> permissionService.update(dto))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class DeleteTest {

        @Test
        @DisplayName("删除权限 - 正常流程")
        void delete_Success() {
            // Given
            when(permissionMapper.selectCount(any())).thenReturn(0L);
            when(permissionMapper.deleteById(1L)).thenReturn(1);

            // When & Then
            assertThatCode(() -> permissionService.delete(1L)).doesNotThrowAnyException();
            verify(permissionMapper).deleteById(1L);
        }

        @Test
        @DisplayName("删除权限 - 存在子权限时不允许删除")
        void delete_HasChildren() {
            // Given
            when(permissionMapper.selectCount(any())).thenReturn(5L);

            // When & Then
            assertThatThrownBy(() -> permissionService.delete(1L))
                    .isInstanceOf(BizException.class);
            verify(permissionMapper, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("getById 方法")
    class GetByIdTest {

        @Test
        @DisplayName("根据ID查询 - 正常流程")
        void getById_Success() {
            // Given
            PermissionDO perm = new PermissionDO();
            perm.setId(1L);
            perm.setPermCode("system:user:create");
            when(permissionMapper.selectById(1L)).thenReturn(perm);

            // When
            PermissionDO result = permissionService.getById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("根据ID查询 - 权限不存在")
        void getById_NotFound() {
            // Given
            when(permissionMapper.selectById(999L)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> permissionService.getById(999L))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("listAllEnabled 方法")
    class ListAllEnabledTest {

        @Test
        @DisplayName("查询所有启用权限 - 正常流程")
        void listAllEnabled_Success() {
            // Given
            PermissionDO perm1 = new PermissionDO();
            perm1.setId(1L);
            perm1.setStatus("ENABLED");
            PermissionDO perm2 = new PermissionDO();
            perm2.setId(2L);
            perm2.setStatus("ENABLED");
            when(permissionMapper.selectList(any())).thenReturn(Arrays.asList(perm1, perm2));

            // When
            List<PermissionDO> result = permissionService.listAllEnabled();

            // Then
            assertThat(result).hasSize(2);
        }
    }
}
