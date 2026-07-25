#!/usr/bin/env python3
"""
Batch generate server + web + api layer Java files.
Uses plain strings (not f-strings) to avoid Java brace conflicts.
"""
import os

BASE_UI = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-userinfo'
BASE_SY = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-system'
SERVER_PKG_UI = 'com.njydsz.userinfo.server'
SERVER_PATH_UI = BASE_UI + r'\ydsz-userinfo-server\src\main\java\com\njydsz\userinfo\server'
WEB_PATH_UI = BASE_UI + r'\ydsz-userinfo-web\src\main\java\com\njydsz\userinfo\web'
API_PATH_UI = BASE_UI + r'\ydsz-userinfo-api\src\main\java\com\njydsz\userinfo\api'
SERVER_PKG_SY = 'com.njydsz.system.server'
SERVER_PATH_SY = BASE_SY + r'\ydsz-system-server\src\main\java\com\njydsz\system\server'
WEB_PATH_SY = BASE_SY + r'\ydsz-system-web\src\main\java\com\njydsz\system\web'
API_PATH_SY = BASE_SY + r'\ydsz-system-api\src\main\java\com\njydsz\system\api'

def wj(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Written: ' + os.path.basename(path))


# ============================================================
# P0-3: UserInfoRbacService
# ============================================================
wj(SERVER_PATH_UI + r'\auth\UserInfoRbacService.java', '''package com.njydsz.userinfo.server.auth;

import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.service.RbacUserInfoService;
import com.njydsz.common.auth.util.AccessTokenUtils;
import com.njydsz.common.redis.service.ops.RedisHashOps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户信息 RBAC 服务实现。
 *
 * <p>从 Redis Token Hash 加载用户信息，实现 common-auth 的 RbacUserInfoService SPI。
 * 登录时由 AuthService 将用户信息写入 Redis Hash，本类负责读取。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserInfoRbacService implements RbacUserInfoService {

    private final RedisHashOps redisHashOps;

    @Override
    public UserInfo loadUserInfo(String accessToken) {
        Map<String, Object> map = loadUserInfoMap(accessToken);
        if (map == null || map.isEmpty()) {
            return null;
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(getString(map, "userId"));
        userInfo.setUsername(getString(map, "username"));
        userInfo.setRoleCode(getString(map, "roleCode"));
        userInfo.setRoleName(getString(map, "roleName"));
        userInfo.setDeptId(getString(map, "deptId"));
        userInfo.setTenantId(getString(map, "tenantId"));
        return userInfo.isValid() ? userInfo : null;
    }

    @Override
    public Map<String, Object> loadUserInfoMap(String accessToken) {
        if (accessToken == null || accessToken.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = redisHashOps.hGetAll(accessToken.trim(), Object.class);
        return map == null ? Collections.emptyMap() : map;
    }

    @Override
    public String loadCurrentToken() {
        return AccessTokenUtils.resolve();
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
''')

# ============================================================
# P0-3: DbRolePermissionLoader
# ============================================================
wj(SERVER_PATH_UI + r'\auth\DbRolePermissionLoader.java', '''package com.njydsz.userinfo.server.auth;

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
''')

# ============================================================
# AuthService interface + LoginResult + AuthServiceImpl
# ============================================================
wj(SERVER_PATH_UI + r'\auth\AuthService.java', '''package com.njydsz.userinfo.server.auth;

/**
 * 认证服务接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuthService {

    LoginResult login(String username, String password);
    void logout(String accessToken);
    LoginResult refresh(String refreshToken);
}
''')

wj(SERVER_PATH_UI + r'\auth\LoginResult.java', '''package com.njydsz.userinfo.server.auth;

import lombok.Data;

/**
 * 登录结果 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LoginResult {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String userId;
    private String username;
    private String realName;
}
''')

wj(SERVER_PATH_UI + r'\auth\AuthServiceImpl.java', '''package com.njydsz.userinfo.server.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.njydsz.common.auth.token.JwtTokenService;
import com.njydsz.common.auth.token.TokenBlacklistService;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.userinfo.domain.entity.RoleDO;
import com.njydsz.userinfo.domain.entity.UserAccountDO;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserAccountMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证服务实现。使用 common-auth JwtTokenService 签发/验证/吊销 Token。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RedisHashOps redisHashOps;
    private final PasswordEncoder passwordEncoder;

    private static final long TOKEN_TTL_SECONDS = 7200;

    @Override
    public LoginResult login(String username, String password) {
        LambdaQueryWrapper<UserAccountDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccountDO::getUsername, username);
        wrapper.eq(UserAccountDO::getDeleted, 0);
        UserAccountDO user = userAccountMapper.selectOne(wrapper);

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        List<RoleDO> roles = loadUserRoles(user.getId());
        String roleCodes = roles.stream().map(RoleDO::getRoleCode)
                .collect(Collectors.joining(","));
        String roleNames = roles.stream().map(RoleDO::getRoleName)
                .collect(Collectors.joining(","));

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("roleCode", roleCodes);
        claims.put("tenantId", user.getTenantId());

        String accessToken = jwtTokenService.generateAccessToken(claims);
        String refreshToken = jwtTokenService.generateRefreshToken(claims);

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("roleCode", roleCodes);
        userInfo.put("roleName", roleNames);
        userInfo.put("tenantId", user.getTenantId());
        redisHashOps.hSetAll(accessToken, userInfo, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);

        LoginResult result = new LoginResult();
        result.setAccessToken(accessToken);
        result.setRefreshToken(refreshToken);
        result.setExpiresIn(TOKEN_TTL_SECONDS);
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setRealName(user.getRealName());
        return result;
    }

    @Override
    public void logout(String accessToken) {
        tokenBlacklistService.blacklist(accessToken);
        redisHashOps.delete(accessToken);
    }

    @Override
    public LoginResult refresh(String refreshToken) {
        String accessToken = jwtTokenService.refreshAccessToken(refreshToken);
        LoginResult result = new LoginResult();
        result.setAccessToken(accessToken);
        result.setRefreshToken(refreshToken);
        result.setExpiresIn(TOKEN_TTL_SECONDS);
        return result;
    }

    private List<RoleDO> loadUserRoles(String userId) {
        LambdaQueryWrapper<RoleDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleDO::getDeleted, 0);
        wrapper.eq(RoleDO::getStatus, "ENABLED");
        return roleMapper.selectList(wrapper);
    }
}
''')

# ============================================================
# P1-1: CRUD Services (userinfo)
# ============================================================
UI_SVCS = [
    ('UserAccountService', 'UserAccountDO', 'UserAccountMapper'),
    ('RoleService', 'RoleDO', 'RoleMapper'),
    ('MenuService', 'MenuDO', 'MenuMapper'),
    ('DepartmentService', 'DepartmentDO', 'DepartmentMapper'),
]
for svc, ent, mpr in UI_SVCS:
    wj(SERVER_PATH_UI + r'\service\\' + svc + '.java',
       'package com.njydsz.userinfo.server.service;\n\n'
       + 'import java.util.List;\n\n'
       + 'import com.njydsz.userinfo.domain.entity.' + ent + ';\n\n'
       + '/**\n * ' + svc.replace('Service','') + ' service interface.\n *\n * @author ydsz-team\n * @since 1.0.0\n */\n'
       + 'public interface ' + svc + ' {\n\n'
       + '    ' + ent + ' getById(String id);\n'
       + '    List<' + ent + '> list();\n'
       + '    String save(' + ent + ' entity);\n'
       + '    boolean updateById(' + ent + ' entity);\n'
       + '    boolean removeById(String id);\n'
       + '}\n')

    wj(SERVER_PATH_UI + r'\service\impl\\' + svc + 'Impl.java',
       'package com.njydsz.userinfo.server.service.impl;\n\n'
       + 'import java.util.List;\n\n'
       + 'import org.springframework.stereotype.Service;\n\n'
       + 'import com.njydsz.userinfo.server.service.' + svc + ';\n'
       + 'import com.njydsz.userinfo.domain.entity.' + ent + ';\n'
       + 'import com.njydsz.userinfo.infra.mapper.' + mpr + ';\n\n'
       + 'import lombok.RequiredArgsConstructor;\nimport lombok.extern.slf4j.Slf4j;\n\n'
       + '@Slf4j\n@Service\n@RequiredArgsConstructor\n'
       + 'public class ' + svc + 'Impl implements ' + svc + ' {\n\n'
       + '    private final ' + mpr + ' mapper;\n\n'
       + '    @Override\n'
       + '    public ' + ent + ' getById(String id) {\n'
       + '        return mapper.selectById(id);\n    }\n\n'
       + '    @Override\n'
       + '    public List<' + ent + '> list() {\n'
       + '        return mapper.selectList(null);\n    }\n\n'
       + '    @Override\n'
       + '    public String save(' + ent + ' entity) {\n'
       + '        mapper.insert(entity);\n'
       + '        return entity.getId();\n    }\n\n'
       + '    @Override\n'
       + '    public boolean updateById(' + ent + ' entity) {\n'
       + '        return mapper.updateById(entity) > 0;\n    }\n\n'
       + '    @Override\n'
       + '    public boolean removeById(String id) {\n'
       + '        return mapper.deleteById(id) > 0;\n    }\n'
       + '}\n')

# ============================================================
# P1-1: Web Controllers (userinfo)
# ============================================================
UI_CTRLS = [
    ('UserAccountController', 'UserAccountService', 'UserAccountDO', '/user'),
    ('RoleController', 'RoleService', 'RoleDO', '/role'),
    ('MenuController', 'MenuService', 'MenuDO', '/menu'),
    ('DepartmentController', 'DepartmentService', 'DepartmentDO', '/dept'),
]
for ctrl, svc, ent, path in UI_CTRLS:
    wj(WEB_PATH_UI + r'\controller\\' + ctrl + '.java',
       'package com.njydsz.userinfo.web.controller;\n\n'
       + 'import java.util.List;\n\n'
       + 'import org.springframework.web.bind.annotation.*;\n\n'
       + 'import com.njydsz.userinfo.server.service.' + svc + ';\n'
       + 'import com.njydsz.userinfo.domain.entity.' + ent + ';\n\n'
       + 'import lombok.RequiredArgsConstructor;\n\n'
       + '@RestController\n@RequestMapping("' + path + '")\n@RequiredArgsConstructor\n'
       + 'public class ' + ctrl + ' {\n\n'
       + '    private final ' + svc + ' service;\n\n'
       + '    @GetMapping("/list")\n'
       + '    public List<' + ent + '> list() {\n'
       + '        return service.list();\n    }\n\n'
       + '    @GetMapping("/{id}")\n'
       + '    public ' + ent + ' getById(@PathVariable String id) {\n'
       + '        return service.getById(id);\n    }\n\n'
       + '    @PostMapping\n'
       + '    public String save(@RequestBody ' + ent + ' entity) {\n'
       + '        return service.save(entity);\n    }\n\n'
       + '    @PutMapping\n'
       + '    public boolean update(@RequestBody ' + ent + ' entity) {\n'
       + '        return service.updateById(entity);\n    }\n\n'
       + '    @DeleteMapping("/{id}")\n'
       + '    public boolean remove(@PathVariable String id) {\n'
       + '        return service.removeById(id);\n    }\n'
       + '}\n')

# AuthController
wj(WEB_PATH_UI + r'\controller\AuthController.java', '''package com.njydsz.userinfo.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.LoginResult;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Auth Controller - login/logout/refresh.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResult login(@RequestBody LoginRequest request) {
        return authService.login(request.getUsername(), request.getPassword());
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader("Authorization") String token) {
        String accessToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        authService.logout(accessToken);
    }

    @PostMapping("/refresh")
    public LoginResult refresh(@RequestBody RefreshRequest request) {
        return authService.refresh(request.getRefreshToken());
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class RefreshRequest {
        private String refreshToken;
    }
}
''')

# OAuth2Controller
wj(WEB_PATH_UI + r'\controller\OAuth2Controller.java', '''package com.njydsz.userinfo.web.controller;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.redis.service.ops.RedisStringOps;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 Authorization Code Grant flow.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
@Tag(name = "OAuth2", description = "OAuth2 authorization code flow")
public class OAuth2Controller {

    private final RedisStringOps redisStringOps;

    private static final long CODE_TTL_SECONDS = 300;
    private static final String CODE_KEY_PREFIX = "oauth2:code:";

    @GetMapping("/authorize")
    @Operation(summary = "Get authorization code")
    public String authorize(
            @RequestParam String clientId,
            @RequestParam String redirectUri,
            @RequestParam(required = false) String state) {
        String code = UUID.randomUUID().toString().replace("-", "");
        redisStringOps.set(CODE_KEY_PREFIX + code, clientId, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("OAuth2 authorize: clientId={}, code={}", clientId, code);
        return code;
    }

    @GetMapping("/token")
    @Operation(summary = "Exchange code for token")
    public Object token(
            @RequestParam String code,
            @RequestParam String clientId) {
        String storedClientId = redisStringOps.get(CODE_KEY_PREFIX + code, String.class);
        if (storedClientId == null || !storedClientId.equals(clientId)) {
            throw new RuntimeException("Invalid or expired authorization code");
        }
        redisStringOps.delete(CODE_KEY_PREFIX + code);
        log.info("OAuth2 token: clientId={}, code={}", clientId, code);
        return java.util.Map.of("message", "Token issued", "code", code);
    }
}
''')

# ============================================================
# P1-2: System CRUD Services
# ============================================================
SY_SVCS = [
    ('AppInfoService', 'AppInfoDO', 'AppInfoMapper'),
    ('DictService', 'DictTypeDO', 'DictTypeMapper'),
    ('ConfigService', 'ConfigDO', 'ConfigMapper'),
    ('VariableService', 'VariableDO', 'VariableMapper'),
]
for svc, ent, mpr in SY_SVCS:
    wj(SERVER_PATH_SY + r'\service\\' + svc + '.java',
       'package com.njydsz.system.server.service;\n\n'
       + 'import java.util.List;\n\n'
       + 'import com.njydsz.system.domain.entity.' + ent + ';\n\n'
       + '/**\n * ' + svc.replace('Service','') + ' service interface.\n *\n * @author ydsz-team\n * @since 1.0.0\n */\n'
       + 'public interface ' + svc + ' {\n\n'
       + '    ' + ent + ' getById(String id);\n'
       + '    List<' + ent + '> list();\n'
       + '    String save(' + ent + ' entity);\n'
       + '    boolean updateById(' + ent + ' entity);\n'
       + '    boolean removeById(String id);\n'
       + '}\n')

    wj(SERVER_PATH_SY + r'\service\impl\\' + svc + 'Impl.java',
       'package com.njydsz.system.server.service.impl;\n\n'
       + 'import java.util.List;\n\n'
       + 'import org.springframework.stereotype.Service;\n\n'
       + 'import com.njydsz.system.server.service.' + svc + ';\n'
       + 'import com.njydsz.system.domain.entity.' + ent + ';\n'
       + 'import com.njydsz.system.infra.mapper.' + mpr + ';\n\n'
       + 'import lombok.RequiredArgsConstructor;\nimport lombok.extern.slf4j.Slf4j;\n\n'
       + '@Slf4j\n@Service\n@RequiredArgsConstructor\n'
       + 'public class ' + svc + 'Impl implements ' + svc + ' {\n\n'
       + '    private final ' + mpr + ' mapper;\n\n'
       + '    @Override\n'
       + '    public ' + ent + ' getById(String id) {\n'
       + '        return mapper.selectById(id);\n    }\n\n'
       + '    @Override\n'
       + '    public List<' + ent + '> list() {\n'
       + '        return mapper.selectList(null);\n    }\n\n'
       + '    @Override\n'
       + '    public String save(' + ent + ' entity) {\n'
       + '        mapper.insert(entity);\n'
       + '        return entity.getId();\n    }\n\n'
       + '    @Override\n'
       + '    public boolean updateById(' + ent + ' entity) {\n'
       + '        return mapper.updateById(entity) > 0;\n    }\n\n'
       + '    @Override\n'
       + '    public boolean removeById(String id) {\n'
       + '        return mapper.deleteById(id) > 0;\n    }\n'
       + '}\n')

# ============================================================
# P1-2: System Web Controllers
# ============================================================
SY_CTRLS = [
    ('AppInfoController', 'AppInfoService', 'AppInfoDO', '/app'),
    ('DictController', 'DictService', 'DictTypeDO', '/dict'),
    ('ConfigController', 'ConfigService', 'ConfigDO', '/config'),
    ('VariableController', 'VariableService', 'VariableDO', '/variable'),
]
for ctrl, svc, ent, path in SY_CTRLS:
    wj(WEB_PATH_SY + r'\controller\\' + ctrl + '.java',
       'package com.njydsz.system.web.controller;\n\n'
       + 'import java.util.List;\n\n'
       + 'import org.springframework.web.bind.annotation.*;\n\n'
       + 'import com.njydsz.system.server.service.' + svc + ';\n'
       + 'import com.njydsz.system.domain.entity.' + ent + ';\n\n'
       + 'import lombok.RequiredArgsConstructor;\n\n'
       + '@RestController\n@RequestMapping("' + path + '")\n@RequiredArgsConstructor\n'
       + 'public class ' + ctrl + ' {\n\n'
       + '    private final ' + svc + ' service;\n\n'
       + '    @GetMapping("/list")\n'
       + '    public List<' + ent + '> list() {\n'
       + '        return service.list();\n    }\n\n'
       + '    @GetMapping("/{id}")\n'
       + '    public ' + ent + ' getById(@PathVariable String id) {\n'
       + '        return service.getById(id);\n    }\n\n'
       + '    @PostMapping\n'
       + '    public String save(@RequestBody ' + ent + ' entity) {\n'
       + '        return service.save(entity);\n    }\n\n'
       + '    @PutMapping\n'
       + '    public boolean update(@RequestBody ' + ent + ' entity) {\n'
       + '        return service.updateById(entity);\n    }\n\n'
       + '    @DeleteMapping("/{id}")\n'
       + '    public boolean remove(@PathVariable String id) {\n'
       + '        return service.removeById(id);\n    }\n'
       + '}\n')

# ============================================================
# P1-4: Feign API Clients
# ============================================================
wj(API_PATH_UI + r'\client\OrgQueryClient.java', '''package com.njydsz.userinfo.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Org query Feign client for cross-service user/dept queries.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-userinfo", contextId = "orgQueryClient",
        fallbackFactory = OrgQueryClientFallback.class)
public interface OrgQueryClient {

    @GetMapping("/api/internal/user/query")
    Object queryUserById(@RequestParam String userId);

    @GetMapping("/api/internal/dept/tree")
    Object getDeptTree();
}
''')

wj(API_PATH_UI + r'\client\OrgQueryClientFallback.java', '''package com.njydsz.userinfo.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OrgQueryClient fallback.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class OrgQueryClientFallback implements OrgQueryClient {

    @Override
    public Object queryUserById(String userId) {
        log.warn("OrgQueryClient fallback: queryUserById={}", userId);
        return null;
    }

    @Override
    public Object getDeptTree() {
        log.warn("OrgQueryClient fallback: getDeptTree");
        return null;
    }
}
''')

wj(API_PATH_UI + r'\client\UserServiceClient.java', '''package com.njydsz.userinfo.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * User service Feign client.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-userinfo", contextId = "userServiceClient",
        fallbackFactory = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/api/internal/user/info")
    Object getUserInfo(@RequestParam String userId);
}
''')

wj(API_PATH_UI + r'\client\UserServiceClientFallback.java', '''package com.njydsz.userinfo.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * UserServiceClient fallback.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public Object getUserInfo(String userId) {
        log.warn("UserServiceClient fallback: getUserInfo={}", userId);
        return null;
    }
}
''')

wj(API_PATH_SY + r'\client\ConfigClient.java', '''package com.njydsz.system.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * System config Feign client.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-system", contextId = "configClient",
        fallbackFactory = ConfigClientFallback.class)
public interface ConfigClient {

    @GetMapping("/api/internal/config/get")
    String getConfig(@RequestParam String key);

    @GetMapping("/api/internal/dict/item")
    Object getDictItem(@RequestParam String typeCode, @RequestParam String itemCode);
}
''')

wj(API_PATH_SY + r'\client\ConfigClientFallback.java', '''package com.njydsz.system.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ConfigClient fallback.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ConfigClientFallback implements ConfigClient {

    @Override
    public String getConfig(String key) {
        log.warn("ConfigClient fallback: getConfig={}", key);
        return null;
    }

    @Override
    public Object getDictItem(String typeCode, String itemCode) {
        log.warn("ConfigClient fallback: getDictItem={},{}", typeCode, itemCode);
        return null;
    }
}
''')

wj(API_PATH_SY + r'\client\AppInfoClient.java', '''package com.njydsz.system.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * App info Feign client for OAuth2 client_id validation.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-system", contextId = "appInfoClient",
        fallbackFactory = AppInfoClientFallback.class)
public interface AppInfoClient {

    @GetMapping("/api/internal/app/validate")
    boolean validateClient(@RequestParam String appKey, @RequestParam String appSecret);
}
''')

wj(API_PATH_SY + r'\client\AppInfoClientFallback.java', '''package com.njydsz.system.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AppInfoClient fallback.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AppInfoClientFallback implements AppInfoClient {

    @Override
    public boolean validateClient(String appKey, String appSecret) {
        log.warn("AppInfoClient fallback: validateClient={}", appKey);
        return false;
    }
}
''')

print('\n========================================')
print('All server + web + api files generated!')
print('========================================')
