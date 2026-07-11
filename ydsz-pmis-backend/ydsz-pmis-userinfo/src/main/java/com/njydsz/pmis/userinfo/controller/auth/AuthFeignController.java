package com.njydsz.pmis.userinfo.controller.auth;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.userinfo.dto.auth.LoginContextDTO;
import com.njydsz.pmis.userinfo.entity.permission.RoleDO;
import com.njydsz.pmis.userinfo.entity.user.UserAccountDO;
import com.njydsz.pmis.userinfo.service.permission.PermissionService;
import com.njydsz.pmis.userinfo.service.permission.RoleService;
import com.njydsz.pmis.userinfo.service.user.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
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
@RequestMapping("/feign/auth")
@RequiredArgsConstructor
public class AuthFeignController {

    /** 用户账号服务 */
    private final UserAccountService userAccountService;
    /** 权限服务 */
    private final PermissionService permissionService;
    /** 角色服务 */
    private final RoleService roleService;

    /**
     * 根据用户名加载登录上下文
     *
     * @param username 用户名
     * @return 统一响应结果，包含登录上下文
     */
    @Operation(summary = "根据用户名加载登录上下文")
    @GetMapping("/context/byUsername")
    public Result<LoginContextDTO> getLoginContextByUsername(@RequestParam String username) {
        UserAccountDO user = userAccountService.findByUsername(username);
        return Result.ok(buildContext(user));
    }

    /**
     * 根据用户 ID 加载登录上下文
     *
     * @param userId 用户 ID
     * @return 统一响应结果，包含登录上下文
     */
    @Operation(summary = "根据用户 ID 加载登录上下文")
    @GetMapping("/context/byId")
    public Result<LoginContextDTO> getLoginContextById(@RequestParam String userId) {
        return Result.ok(buildContext(userAccountService.findById(userId)));
    }

    /**
     * 根据用户实体构建登录上下文（含角色与权限编码）
     *
     * @param user 用户实体
     * @return 登录上下文，用户为空时返回 null
     */
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
                        : user.getLockedUntil().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

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
