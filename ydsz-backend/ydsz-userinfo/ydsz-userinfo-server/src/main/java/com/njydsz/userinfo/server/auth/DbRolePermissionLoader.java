package com.njydsz.userinfo.server.auth;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.RolePermissions;
import com.njydsz.common.auth.service.RolePermissionLoader;
import com.njydsz.userinfo.domain.entity.Menu;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.RolePermission;
import com.njydsz.userinfo.infra.mapper.MenuMapper;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.RolePermissionMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于数据库的角色权限加载器。
 *
 * <p>从 ydsz_role_permission 关联表按 roleId 查询权限 ID，
 * 再从 ydsz_menu（权限表）加载菜单/按钮/API 权限集合。
 * 实现 common-auth 的 RolePermissionLoader SPI。
 *
 * <p>修复 P0-2 Bug：原实现未按 roleCode 过滤，加载了全部菜单权限。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbRolePermissionLoader implements RolePermissionLoader {

    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public RolePermissions loadByRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return RolePermissions.empty();
        }
        try {
            // 1. 按 roleCode 查询角色 ID
            LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
            roleWrapper.eq(Role::getRoleCode, roleCode);
            roleWrapper.eq(Role::getDeleted, 0);
            Role role = roleMapper.selectOne(roleWrapper);

            if (role == null) {
                log.debug("Role not found for roleCode: {}", roleCode);
                return RolePermissions.empty();
            }

            // 2. 按 roleId 查询 role_permission 关联表
            LambdaQueryWrapper<RolePermission> rpWrapper = new LambdaQueryWrapper<>();
            rpWrapper.eq(RolePermission::getRoleId, role.getId());
            rpWrapper.eq(RolePermission::getDeleted, 0);
            List<RolePermission> rolePermissions = rolePermissionMapper.selectList(rpWrapper);

            if (rolePermissions.isEmpty()) {
                return RolePermissions.empty();
            }

            // 3. 提取 permissionId 列表（即 menuId）
            List<String> permissionIds = rolePermissions.stream()
                    .map(RolePermission::getPermissionId)
                    .collect(Collectors.toList());

            // 4. 查询权限/菜单详情
            LambdaQueryWrapper<Menu> menuWrapper = new LambdaQueryWrapper<>();
            menuWrapper.in(Menu::getId, permissionIds);
            menuWrapper.eq(Menu::getDeleted, 0);
            menuWrapper.eq(Menu::getStatus, "ENABLED");
            List<Menu> menus = menuMapper.selectList(menuWrapper);

            // 5. 按类型分类权限码
            Set<String> menuPerms = new HashSet<>();
            Set<String> buttonPerms = new HashSet<>();
            Set<String> apiPerms = new HashSet<>();

            for (Menu menu : menus) {
                String permCode = menu.getPermissionCode();
                if (permCode == null || permCode.isBlank()) {
                    continue;
                }
                String type = menu.getMenuType();
                if ("BUTTON".equals(type)) {
                    buttonPerms.add(permCode);
                } else if ("API".equals(type)) {
                    apiPerms.add(permCode);
                } else {
                    menuPerms.add(permCode);
                }
            }

            return new RolePermissions(
                Collections.unmodifiableSet(menuPerms),
                Collections.unmodifiableSet(buttonPerms),
                Collections.unmodifiableSet(apiPerms)
            );
        } catch (Exception e) {
            log.error("Failed to load permissions for role: {}", roleCode, e);
            return RolePermissions.empty();
        }
    }
}
