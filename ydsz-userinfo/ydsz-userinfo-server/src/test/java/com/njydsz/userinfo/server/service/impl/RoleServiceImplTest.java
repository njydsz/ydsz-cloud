package com.njydsz.userinfo.server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.auth.event.PermissionChangeNotifier;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.userinfo.domain.dto.RoleDTO;
import com.njydsz.userinfo.domain.dto.RolePermissionDTO;
import com.njydsz.userinfo.domain.query.RolePageQuery;
import com.njydsz.userinfo.domain.repository.RolePermissionRepository;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.repository.UserRoleRepository;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.server.auth.DbRolePermissionLoader;
import com.njydsz.userinfo.server.event.UserDomainEventPublisher;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @InjectMocks
    private RoleServiceImpl roleService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RedisStringOps redisStringOps;

    @Mock
    private UserDomainEventPublisher eventPublisher;

    @Mock
    private PermissionChangeNotifier permissionChangeNotifier;

    @Mock
    private DbRolePermissionLoader permissionLoader;

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("getById - existing role returns VO")
        void getById_existingRole_returnsVo() {
            RoleVO vo = new RoleVO();
            vo.setId("role-001");
            vo.setRoleCode("ADMIN");
            when(roleRepository.findById("role-001")).thenReturn(Optional.of(vo));

            RoleVO result = roleService.getById("role-001");

            assertThat(result).isNotNull();
            assertThat(result.getRoleCode()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("getById - non-existing role throws exception")
        void getById_nonExistingRole_throwsException() {
            when(roleRepository.findById("role-999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roleService.getById("role-999"))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("create - unique roleCode succeeds with defaults")
        void create_uniqueCodeWithDefaults_succeeds() {
            RoleDTO dto = new RoleDTO();
            dto.setRoleCode("MANAGER");
            dto.setRoleName("Manager");

            RoleVO saved = new RoleVO();
            saved.setId("role-new");
            saved.setRoleCode("MANAGER");

            when(roleRepository.countByQuery(any(RolePageQuery.class))).thenReturn(0L);
            when(roleRepository.save(any(RoleDTO.class))).thenReturn(saved);

            String result = roleService.create(dto);

            assertThat(result).isEqualTo("role-new");
            verify(roleRepository).save(dto);
            verify(eventPublisher).publishRoleEntityChanged(saved, "CREATED");
        }

        @Test
        @DisplayName("create - duplicate roleCode throws exception")
        void create_duplicateCode_throwsException() {
            RoleDTO dto = new RoleDTO();
            dto.setRoleCode("ADMIN");
            dto.setRoleName("Duplicate");

            when(roleRepository.countByQuery(any(RolePageQuery.class))).thenReturn(1L);

            assertThatThrownBy(() -> roleService.create(dto))
                    .isInstanceOf(BusinessException.class);

            verify(roleRepository, never()).save(any(RoleDTO.class));
        }
    }

    @Nested
    @DisplayName("removeById")
    class RemoveById {

        @Test
        @DisplayName("removeById - non-existing role throws exception")
        void removeById_nonExistingRole_throwsException() {
            when(roleRepository.findById("role-999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roleService.removeById("role-999"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("removeById - builtin role throws exception")
        void removeById_builtinRole_throwsException() {
            RoleVO builtin = new RoleVO();
            builtin.setId("role-001");
            builtin.setRoleCode("ADMIN");
            builtin.setIsBuiltIn(true);
            when(roleRepository.findById("role-001")).thenReturn(Optional.of(builtin));

            assertThatThrownBy(() -> roleService.removeById("role-001"))
                    .isInstanceOf(BusinessException.class);

            verify(roleRepository, never()).deleteById(anyString());
        }

        @Test
        @DisplayName("removeById - role with users throws exception")
        void removeById_roleWithUsers_throwsException() {
            RoleVO role = new RoleVO();
            role.setId("role-002");
            role.setRoleCode("USER");
            role.setIsBuiltIn(false);
            when(roleRepository.findById("role-002")).thenReturn(Optional.of(role));
            when(userRoleRepository.countByRoleId("role-002")).thenReturn(5L);

            assertThatThrownBy(() -> roleService.removeById("role-002"))
                    .isInstanceOf(BusinessException.class);

            verify(roleRepository, never()).deleteById(anyString());
        }

        @Test
        @DisplayName("removeById - normal role deletes associated permissions first")
        void removeById_normalRole_deletesPermissionsFirst() {
            RoleVO role = new RoleVO();
            role.setId("role-003");
            role.setRoleCode("CUSTOM");
            role.setIsBuiltIn(false);
            when(roleRepository.findById("role-003")).thenReturn(Optional.of(role));
            when(userRoleRepository.countByRoleId("role-003")).thenReturn(0L);
            when(roleRepository.deleteById("role-003")).thenReturn(true);

            boolean result = roleService.removeById("role-003");

            assertThat(result).isTrue();
            verify(rolePermissionRepository).deleteByRoleId("role-003");
            verify(roleRepository).deleteById("role-003");
            verify(eventPublisher).publishRoleEntityChanged(role, "DELETED");
        }
    }

    @Nested
    @DisplayName("assignPermissions")
    class AssignPermissions {

        @Test
        @DisplayName("assignPermissions - non-existing role throws exception")
        void assignPermissions_nonExistingRole_throwsException() {
            when(roleRepository.findById("role-999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> roleService.assignPermissions("role-999", List.of("p1")))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("assignPermissions - empty list clears permissions only")
        void assignPermissions_emptyList_clearsOnly() {
            RoleVO role = new RoleVO();
            role.setId("role-001");
            role.setRoleCode("ADMIN");
            when(roleRepository.findById("role-001")).thenReturn(Optional.of(role));

            boolean result = roleService.assignPermissions("role-001", List.of());

            assertThat(result).isTrue();
            verify(rolePermissionRepository).deleteByRoleId("role-001");
            verify(rolePermissionRepository, never()).batchInsert(any());
        }

        @Test
        @DisplayName("assignPermissions - null permission list clears only")
        void assignPermissions_nullList_clearsOnly() {
            RoleVO role = new RoleVO();
            role.setId("role-001");
            role.setRoleCode("ADMIN");
            when(roleRepository.findById("role-001")).thenReturn(Optional.of(role));

            boolean result = roleService.assignPermissions("role-001", null);

            assertThat(result).isTrue();
            verify(rolePermissionRepository).deleteByRoleId("role-001");
            verify(rolePermissionRepository, never()).batchInsert(any());
        }

        @Test
        @DisplayName("assignPermissions - non-empty list batch inserts")
        void assignPermissions_nonEmptyList_batchInserts() {
            RoleVO role = new RoleVO();
            role.setId("role-001");
            role.setRoleCode("ADMIN");
            when(roleRepository.findById("role-001")).thenReturn(Optional.of(role));

            List<String> permIds = List.of("p1", "p2", "p3");
            boolean result = roleService.assignPermissions("role-001", permIds);

            assertThat(result).isTrue();
            verify(rolePermissionRepository).deleteByRoleId("role-001");
            verify(rolePermissionRepository).batchInsert(any());
        }
    }

    @Nested
    @DisplayName("getRolePermissionIds")
    class GetRolePermissionIds {

        @Test
        @DisplayName("getRolePermissionIds - cache hit returns cached data")
        void getRolePermissionIds_cacheHit_returnsCached() {
            List<String> cached = List.of("p1", "p2");
            when(redisStringOps.get(anyString(), eq(String.class)))
                    .thenReturn("[\"p1\",\"p2\"]");

            List<String> result = roleService.getRolePermissionIds("role-001");

            assertThat(result).hasSize(2).containsExactly("p1", "p2");
        }

        @Test
        @DisplayName("getRolePermissionIds - cache miss queries DB and caches")
        void getRolePermissionIds_cacheMiss_queriesDb() {
            when(redisStringOps.get(anyString(), eq(String.class))).thenReturn(null);
            when(rolePermissionRepository.findPermissionIdsByRoleId("role-001"))
                    .thenReturn(List.of("p1", "p3"));

            List<String> result = roleService.getRolePermissionIds("role-001");

            assertThat(result).hasSize(2).containsExactly("p1", "p3");
            verify(redisStringOps).set(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("getRolePermissionIds - empty cache returns DB data")
        void getRolePermissionIds_emptyCache_returnsDbData() {
            when(redisStringOps.get(anyString(), eq(String.class))).thenReturn("");
            when(rolePermissionRepository.findPermissionIdsByRoleId("role-001"))
                    .thenReturn(List.of());

            List<String> result = roleService.getRolePermissionIds("role-001");

            assertThat(result).isEmpty();
        }
    }
}
