package com.njydsz.userinfo.server.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.auth.event.PermissionChangeNotifier;
import com.njydsz.userinfo.domain.dto.ApiPermissionRegistrationDTO;
import com.njydsz.userinfo.domain.dto.MenuDTO;
import com.njydsz.userinfo.domain.repository.MenuRepository;
import com.njydsz.userinfo.domain.repository.RolePermissionRepository;
import com.njydsz.userinfo.domain.repository.RoleRepository;
import com.njydsz.userinfo.domain.vo.MenuVO;
import com.njydsz.userinfo.server.auth.DbRolePermissionLoader;

/**
 * {@link MenuServiceImpl} 单元测试。
 *
 * <p>聚焦 API 权限注册逻辑（新增/更新两条路径），验证权限编码生成、Repository 委托和缓存失效。
 *
 * @author ydsz-team
 * @since 26.10.30
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceImplTest {

  @InjectMocks private MenuServiceImpl menuService;

  @Mock private MenuRepository menuRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private RolePermissionRepository rolePermissionRepository;
  @Mock private PermissionChangeNotifier permissionChangeNotifier;
  @Mock private DbRolePermissionLoader permissionLoader;

  @Test
  @DisplayName("registerApiPermission - 新增权限项时生成稳定编码并保存")
  void registerApiPermission_newEntry_savesAndReturnsId() {
    ApiPermissionRegistrationDTO dto = new ApiPermissionRegistrationDTO();
    dto.setApiCode("system:user:create");
    dto.setApiName("创建用户");
    dto.setServiceName("ydsz-system");

    when(menuRepository.findByMenuCode(any(String.class))).thenReturn(Optional.empty());
    when(menuRepository.save(any(MenuDTO.class))).thenAnswer(invocation -> {
      MenuDTO saved = invocation.getArgument(0);
      MenuVO vo = new MenuVO();
      vo.setId("menu-api-001");
      vo.setMenuCode(saved.getMenuCode());
      return vo;
    });

    String id = menuService.registerApiPermission(dto);

    assertThat(id).isEqualTo("menu-api-001");
    verify(menuRepository).save(any(MenuDTO.class));
    verify(permissionLoader).invalidateAll();
  }

  @Test
  @DisplayName("registerApiPermission - 已存在权限项时更新并返回原 ID")
  void registerApiPermission_existingEntry_updatesAndReturnsOriginalId() {
    ApiPermissionRegistrationDTO dto = new ApiPermissionRegistrationDTO();
    dto.setApiCode("system:user:create");
    dto.setApiName("创建用户（更新）");
    dto.setServiceName("ydsz-system");

    MenuVO existing = new MenuVO();
    existing.setId("menu-api-existing");
    existing.setMenuCode("api:existing");

    when(menuRepository.findByMenuCode(any(String.class))).thenReturn(Optional.of(existing));

    String id = menuService.registerApiPermission(dto);

    assertThat(id).isEqualTo("menu-api-existing");
    verify(menuRepository).save(any(MenuDTO.class));
    verify(permissionLoader).invalidateAll();
  }
}
