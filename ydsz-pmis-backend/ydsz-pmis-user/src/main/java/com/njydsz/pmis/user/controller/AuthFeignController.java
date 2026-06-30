package com.njydsz.pmis.user.controller;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.user.dto.LoginContextDTO;
import com.njydsz.pmis.user.entity.RoleDO;
import com.njydsz.pmis.user.entity.UserAccountDO;
import com.njydsz.pmis.user.service.PermissionService;
import com.njydsz.pmis.user.service.RoleService;
import com.njydsz.pmis.user.service.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 认证相关 Feign 端点
 *
 * <p>仅供 auth 服务远程调用，不对外暴露文档。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "Feign-认证上下文")
@RestController
@RequestMapping("/api/v1/feign/auth")
@RequiredArgsConstructor
public class AuthFeignController {

    private final UserAccountService userAccountService;
    private final PermissionService permissionService;
    private final RoleService roleService;

    @Operation(summary = "根据用户名加载登录上下文")
    @GetMapping("/context/by-username")
    public R<LoginContextDTO> getLoginContextByUsername(@RequestParam String username) {
        UserAccountDO user = userAccountService.findByUsername(username);
        return R.ok(buildContext(user));
    }

    @Operation(summary = "根据用户 ID 加载登录上下文")
    @GetMapping("/context/by-id")
    public R<LoginContextDTO> getLoginContextById(@RequestParam Long userId) {
        return R.ok(buildContext(userAccountService.findById(userId)));
    }

    private LoginContextDTO buildContext(UserAccountDO user) {
        if (user == null) {
            return null;
        }
        LoginContextDTO.LoginContextDTOBuilder builder = LoginContextDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .salt(user.getSalt())
                .status(user.getStatus())
                .loginFailCount(user.getLoginFailCount() == null ? 0 : user.getLoginFailCount())
                .lockedUntil(user.getLockedUntil() == null ? null
                        : user.getLockedUntil().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

        // 角色编码列表
        try {
            List<RoleDO> roles = roleService.listByUserId(user.getId());
            if (roles != null && !roles.isEmpty()) {
                builder.roles(roles.stream().map(RoleDO::getRoleCode).toList());
            } else {
                builder.roles(Collections.emptyList());
            }
        } catch (Exception ignore) {
            builder.roles(Collections.emptyList());
        }

        // 权限编码列表
        try {
            List<String> perms = permissionService.listPermCodesByUserId(user.getId());
            builder.permissions(perms == null ? Collections.emptyList() : perms);
        } catch (Exception ignore) {
            builder.permissions(Collections.emptyList());
        }

        return builder.build();
    }
}
