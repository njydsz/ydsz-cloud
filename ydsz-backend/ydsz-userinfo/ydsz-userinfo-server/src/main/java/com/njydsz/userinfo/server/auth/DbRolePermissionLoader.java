package com.njydsz.userinfo.server.auth;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.RolePermissions;
import com.njydsz.common.auth.service.RolePermissionLoader;
import com.njydsz.userinfo.domain.entity.MenuDO;
import com.njydsz.userinfo.infra.mapper.MenuMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于数据库的角色权限加载器。
 *
 * <p>从 ydsz_menu 表加载角色的菜单/按钮/API 权限集合，
 * 实现 common-auth 的 RolePermissionLoader SPI。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbRolePermissionLoader implements RolePermissionLoader {

    private final MenuMapper menuMapper;

    @Override
    public RolePermissions loadByRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return RolePermissions.empty();
        }
        try {
            Set<String> menuPerms = new HashSet<>();
            Set<String> buttonPerms = new HashSet<>();
            Set<String> apiPerms = new HashSet<>();

            LambdaQueryWrapper<MenuDO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MenuDO::getDeleted, 0);
            wrapper.eq(MenuDO::getStatus, "ENABLED");
            List<MenuDO> menus = menuMapper.selectList(wrapper);

            for (MenuDO menu : menus) {
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
