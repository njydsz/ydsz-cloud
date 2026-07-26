package com.njydsz.common.auth.precheck;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.model.ColumnPermissionInfo;
import com.njydsz.common.auth.model.ColumnScopeInfo;
import com.njydsz.common.auth.model.DataScopeInfo;
import com.njydsz.common.auth.model.RolePermissions;
import com.njydsz.common.auth.service.ColumnPermissionResolver;
import com.njydsz.common.auth.service.DataPermissionResolver;
import com.njydsz.common.auth.service.RbacUserInfoService;
import com.njydsz.common.auth.service.RolePermissionLoader;
import com.njydsz.common.auth.util.PermissionUtils;
import com.njydsz.common.util.string.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 权限预检服务。
 *
 * <p>在业务逻辑执行前预先校验用户是否拥有所需权限，返回详细的预检结果。
 * 与直接抛出异常不同，预检结果可以由业务方自行决定如何处理。
 *
 * <p><b>与切面校验的区别：</b>
 * <ul>
 *   <li>切面校验：校验失败直接抛出异常，打断请求</li>
 *   <li>预检服务：返回详细的预检结果，业务方自行决定处理方式</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>前端根据预检结果动态显示/隐藏操作按钮</li>
 *   <li>批量操作前检查用户是否有权限执行</li>
 *   <li>权限变更前的模拟校验</li>
 *   <li>微服务间调用前的权限校验</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 检查用户是否有某个 API 权限
 * PermissionCheckResult result = preChecker.checkApiPermissions("sys:user:add");
 * if (result.isCheckPassed()) {
 *     userService.addUser(userDTO);
 * } else {
 *     return Response.error("您没有新增用户的权限");
 * }
 *
 * // 批量检查多个权限
 * PermissionCheckResult result = preChecker.checkPermissions(
 *     PermissionType.API,
 *     Set.of("sys:user:add", "sys:user:edit"),
 *     PermissionCheckMode.ALL
 * );
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see PermissionCheckResult
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionPreChecker {

    private final AuthProperties properties;
    private final RbacUserInfoService userInfoService;
    private final RolePermissionLoader rolePermissionLoader;
    private final DataPermissionResolver dataPermissionResolver;
    private final ColumnPermissionResolver columnPermissionResolver;

    /**
     * 权限预检模式。
     *
     * <ul>
     *   <li>ALL：需要全部权限都满足</li>
     *   <li>ANY：只需满足其中一个权限</li>
     * </ul>
     */
    public enum PermissionCheckMode {
        ALL,
        ANY
    }

    /**
     * 权限类型。
     *
     * <ul>
     *   <li>MENU：菜单权限</li>
     *   <li>BUTTON：按钮权限</li>
     *   <li>API：接口权限</li>
     * </ul>
     */
    public enum PermissionType {
        MENU,
        BUTTON,
        API
    }

    /**
     * 预检当前用户是否拥有指定的 API 权限（默认 ALL 模式）。
     *
     * @param apiCodes API 权限码可变参数
     * @return 预检结果
     */
    public PermissionCheckResult checkApiPermissions(String... apiCodes) {
        return checkApiPermissions(Arrays.asList(apiCodes), PermissionCheckMode.ALL);
    }

    /**
     * 预检当前用户是否拥有指定的 API 权限。
     *
     * @param apiCodes API 权限码集合
     * @param mode 预检模式（ALL/ANY）
     * @return 预检结果
     */
    public PermissionCheckResult checkApiPermissions(Collection<String> apiCodes, PermissionCheckMode mode) {
        return checkPermissions(PermissionType.API, new HashSet<>(apiCodes), mode);
    }

    /**
     * 预检当前用户是否拥有指定的菜单权限。
     *
     * @param menuCodes 菜单权限码
     * @return 预检结果
     */
    public PermissionCheckResult checkMenuPermissions(String... menuCodes) {
        return checkMenuPermissions(Arrays.asList(menuCodes), PermissionCheckMode.ALL);
    }

    /**
     * 预检当前用户是否拥有指定的菜单权限。
     *
     * @param menuCodes 菜单权限码集合
     * @param mode 预检模式（ALL/ANY）
     * @return 预检结果
     */
    public PermissionCheckResult checkMenuPermissions(Collection<String> menuCodes, PermissionCheckMode mode) {
        return checkPermissions(PermissionType.MENU, new HashSet<>(menuCodes), mode);
    }

    /**
     * 预检当前用户是否拥有指定的按钮权限。
     *
     * @param buttonCodes 按钮权限码
     * @return 预检结果
     */
    public PermissionCheckResult checkButtonPermissions(String... buttonCodes) {
        return checkButtonPermissions(Arrays.asList(buttonCodes), PermissionCheckMode.ALL);
    }

    /**
     * 预检当前用户是否拥有指定的按钮权限。
     *
     * @param buttonCodes 按钮权限码集合
     * @param mode 预检模式（ALL/ANY）
     * @return 预检结果
     */
    public PermissionCheckResult checkButtonPermissions(Collection<String> buttonCodes, PermissionCheckMode mode) {
        return checkPermissions(PermissionType.BUTTON, new HashSet<>(buttonCodes), mode);
    }

    /**
     * 通用权限预检。
     *
     * @param type 权限类型
     * @param requiredPermissions 需要校验的权限码集合
     * @param mode 预检模式
     * @return 预检结果
     */
    public PermissionCheckResult checkPermissions(PermissionType type, Set<String> requiredPermissions,
                                                 PermissionCheckMode mode) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return PermissionCheckResult.pass("无需校验权限");
        }

        Map<String, Object> userInfo = loadCurrentUserInfo();
        if (userInfo == null || userInfo.isEmpty()) {
            return PermissionCheckResult.deny(
                    "用户未登录",
                    requiredPermissions,
                    null
            );
        }

        Set<String> userRoles = parseUserRoles(userInfo);
        if (isSuperAdmin(userRoles)) {
            return PermissionCheckResult.pass("超级管理员，拥有所有权限");
        }

        RolePermissions rolePermissions = loadRolePermissions(userRoles);
        Set<String> grantedPermissions = getGrantedPermissions(rolePermissions, type);
        String userId = resolveUserId(userInfo);

        Set<String> matched = new HashSet<>();
        Set<String> missing = new HashSet<>();

        for (String required : requiredPermissions) {
            if (hasPermission(grantedPermissions, required)) {
                matched.add(required);
            } else {
                missing.add(required);
            }
        }

        boolean passed;
        if (mode == PermissionCheckMode.ALL) {
            passed = missing.isEmpty();
        } else {
            passed = !matched.isEmpty();
        }

        if (passed) {
            return PermissionCheckResult.builder()
                    .checkPassed(true)
                    .hasPermission(true)
                    .grantedPermissions(grantedPermissions)
                    .message(formatMessage(type, matched, mode))
                    .userId(userId)
                    .userRoles(userRoles)
                    .build();
        } else {
            return PermissionCheckResult.builder()
                    .checkPassed(false)
                    .hasPermission(false)
                    .missingPermissions(missing)
                    .grantedPermissions(grantedPermissions)
                    .message(formatDenyMessage(type, missing, mode))
                    .suggestion(formatSuggestion(type, missing))
                    .errorCode("A03000")
                    .userId(userId)
                    .userRoles(userRoles)
                    .build();
        }
    }

    /**
     * 预检指定角色是否拥有指定权限（用于权限管理模拟）。
     *
     * @param roleCode 角色编码
     * @param type 权限类型
     * @param requiredPermissions 需要校验的权限码
     * @param mode 预检模式
     * @return 预检结果
     */
    public PermissionCheckResult checkPermissionsForRole(String roleCode, PermissionType type,
                                                        Set<String> requiredPermissions,
                                                        PermissionCheckMode mode) {
        if (StringUtils.isBlank(roleCode) || requiredPermissions == null || requiredPermissions.isEmpty()) {
            return PermissionCheckResult.pass("无需校验");
        }

        RolePermissions rolePermissions = rolePermissionLoader.loadByRoleCode(roleCode);
        Set<String> grantedPermissions = getGrantedPermissions(rolePermissions, type);

        Set<String> matched = new HashSet<>();
        Set<String> missing = new HashSet<>();

        for (String required : requiredPermissions) {
            if (hasPermission(grantedPermissions, required)) {
                matched.add(required);
            } else {
                missing.add(required);
            }
        }

        boolean passed = (mode == PermissionCheckMode.ALL) ? missing.isEmpty() : !matched.isEmpty();

        if (passed) {
            return PermissionCheckResult.builder()
                    .checkPassed(true)
                    .hasPermission(true)
                    .grantedPermissions(grantedPermissions)
                    .message(formatMessage(type, matched, mode))
                    .userRoles(Set.of(roleCode))
                    .build();
        } else {
            return PermissionCheckResult.builder()
                    .checkPassed(false)
                    .hasPermission(false)
                    .missingPermissions(missing)
                    .grantedPermissions(grantedPermissions)
                    .message(formatDenyMessage(type, missing, mode))
                    .errorCode("A03000")
                    .userRoles(Set.of(roleCode))
                    .build();
        }
    }

    /**
     * 批量预检多个权限类型。
     *
     * @param checks 预检项列表
     * @return 所有预检项的结果
     */
    public List<PermissionCheckResult> checkBatch(List<PermissionCheckItem> checks) {
        if (checks == null || checks.isEmpty()) {
            return Collections.emptyList();
        }
        List<PermissionCheckResult> results = new ArrayList<>();
        for (PermissionCheckItem check : checks) {
            results.add(checkPermissions(check.type, check.permissions, check.mode));
        }
        return results;
    }

    private Map<String, Object> loadCurrentUserInfo() {
        String token = userInfoService.loadCurrentToken();
        if (StringUtils.isBlank(token)) {
            return Collections.emptyMap();
        }
        return userInfoService.loadUserInfoMap(token);
    }

    private Set<String> parseUserRoles(Map<String, Object> userInfo) {
        if (userInfo == null || userInfo.isEmpty()) {
            return Collections.emptySet();
        }
        Object roleCode = userInfo.get("roleCode");
        if (roleCode == null) {
            return Collections.emptySet();
        }
        return Arrays.stream(String.valueOf(roleCode).split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private boolean isSuperAdmin(Set<String> userRoles) {
        return PermissionUtils.isSuperAdmin(userRoles, properties.getIgnoreRoles());
    }

    private RolePermissions loadRolePermissions(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return RolePermissions.empty();
        }
        Set<String> menu = new HashSet<>();
        Set<String> button = new HashSet<>();
        Set<String> api = new HashSet<>();
        // 使用批量加载替代逐个加载，将 N 次 Redis 往返减少为 2 次（MGET）
        Map<String, RolePermissions> permissionsMap = rolePermissionLoader.loadByRoleCodes(roleCodes);
        for (RolePermissions rp : permissionsMap.values()) {
            if (rp == null) {
                continue;
            }
            if (rp.getMenuPermissions() != null) menu.addAll(rp.getMenuPermissions());
            if (rp.getButtonPermissions() != null) button.addAll(rp.getButtonPermissions());
            if (rp.getApiPermissions() != null) api.addAll(rp.getApiPermissions());
        }
        return new RolePermissions(Collections.unmodifiableSet(menu),
                Collections.unmodifiableSet(button), Collections.unmodifiableSet(api));
    }

    private Set<String> getGrantedPermissions(RolePermissions rp, PermissionType type) {
        if (rp == null) {
            return Collections.emptySet();
        }
        switch (type) {
            case MENU:
                return rp.getMenuPermissions();
            case BUTTON:
                return rp.getButtonPermissions();
            case API:
                return rp.getApiPermissions();
            default:
                return Collections.emptySet();
        }
    }

    private boolean hasPermission(Set<String> granted, String required) {
        return PermissionUtils.hasPermission(granted, required, properties.isWildcardEnabled());
    }

    /**
     * 预检当前用户的行级数据权限范围。
     *
     * <p>返回当前用户可访问的数据维度（租户/公司/部门/项目/区域等），
     * 前端可据此动态控制 UI 显示。
     *
     * @return 预检结果，包含数据权限范围信息
     */
    public PermissionCheckResult checkRowPermission() {
        try {
            DataScopeInfo dataScope = dataPermissionResolver.resolve();
            boolean hasDataScope = dataScope != null && (
                    dataScope.getScope() != null
                    || StringUtils.isNotBlank(dataScope.getTenantId())
                    || StringUtils.isNotBlank(dataScope.getUserId())
                    || (dataScope.getCompanyIds() != null && !dataScope.getCompanyIds().isEmpty())
                    || (dataScope.getDeptIds() != null && !dataScope.getDeptIds().isEmpty())
                    || (dataScope.getProjectIds() != null && !dataScope.getProjectIds().isEmpty())
                    || (dataScope.getRegionIds() != null && !dataScope.getRegionIds().isEmpty())
            );
            return PermissionCheckResult.builder()
                    .checkPassed(hasDataScope)
                    .hasPermission(hasDataScope)
                    .message(hasDataScope ? "行级数据权限范围已解析" : "无行级数据权限")
                    .suggestion(hasDataScope ? null : "请联系管理员配置数据权限")
                    .build();
        } catch (Exception e) {
            log.warn("行权限预检失败: {}", e.getMessage());
            return PermissionCheckResult.builder()
                    .checkPassed(false)
                    .hasPermission(false)
                    .message("行权限预检异常: " + e.getMessage())
                    .suggestion("请稍后重试或联系管理员")
                    .build();
        }
    }

    /**
     * 预检当前用户的列级权限，返回无权限的字段列表。
     *
     * @param table 目标表名
     * @return 预检结果，包含不可读的字段列表
     */
    public PermissionCheckResult checkColumnPermission(String table) {
        try {
            ColumnScopeInfo scopeInfo = columnPermissionResolver.resolve();
            if (scopeInfo == null) {
                return PermissionCheckResult.builder()
                        .checkPassed(true)
                        .hasPermission(true)
                        .message("列权限无配置，默认全部可见")
                        .build();
            }
            Set<String> visibleColumns = scopeInfo.getVisibleColumns(table);
            Set<String> hiddenColumns = new HashSet<>();
            if (visibleColumns != null && !visibleColumns.isEmpty()) {
                // 在有配置的情况下，不在 visible 集合中的字段为隐藏字段
                hiddenColumns.add("* (仅可见: " + String.join(", ", visibleColumns) + ")");
            }
            boolean hasColumnPermission = hiddenColumns.isEmpty();
            return PermissionCheckResult.builder()
                    .checkPassed(hasColumnPermission)
                    .hasPermission(hasColumnPermission)
                    .missingPermissions(hiddenColumns)
                    .message(hasColumnPermission ? "列权限检查通过" : "部分字段无读权限")
                    .suggestion(hasColumnPermission ? null : "仅可见字段: " + (visibleColumns != null ? String.join(", ", visibleColumns) : "无"))
                    .build();
        } catch (Exception e) {
            log.warn("列权限预检失败: {}", e.getMessage());
            return PermissionCheckResult.builder()
                    .checkPassed(false)
                    .hasPermission(false)
                    .message("列权限预检异常: " + e.getMessage())
                    .suggestion("请稍后重试或联系管理员")
                    .build();
        }
    }

    private String resolveUserId(Map<String, Object> userInfo) {
        Object userId = userInfo.get("userId");
        return userId != null ? String.valueOf(userId) : null;
    }

    private String formatMessage(PermissionType type, Set<String> matched, PermissionCheckMode mode) {
        String typeName = type.name().toLowerCase();
        String modeName = mode == PermissionCheckMode.ALL ? "全部满足" : "部分满足";
        return String.format("拥有 %s 权限 [%s]：%s", typeName, modeName, String.join(", ", matched));
    }

    private String formatDenyMessage(PermissionType type, Set<String> missing, PermissionCheckMode mode) {
        String typeName = type.name().toLowerCase();
        String modeName = mode == PermissionCheckMode.ALL ? "需要全部" : "需要至少一个";
        return String.format("缺少 %s 权限（%s）：%s", typeName, modeName, String.join(", ", missing));
    }

    private String formatSuggestion(PermissionType type, Set<String> missing) {
        return String.format("请联系管理员为您授予 [%s] 权限", String.join(", ", missing));
    }

    /**
     * 权限预检项，用于批量预检时封装单次预检参数。
     */
    public static class PermissionCheckItem {
        private PermissionType type;
        private Set<String> permissions;
        private PermissionCheckMode mode;

        public PermissionCheckItem(PermissionType type, Set<String> permissions, PermissionCheckMode mode) {
            this.type = type;
            this.permissions = permissions;
            this.mode = mode;
        }

        public static PermissionCheckItem of(PermissionType type, Set<String> permissions) {
            return new PermissionCheckItem(type, permissions, PermissionCheckMode.ALL);
        }

        public static PermissionCheckItem ofAny(PermissionType type, Set<String> permissions) {
            return new PermissionCheckItem(type, permissions, PermissionCheckMode.ANY);
        }
    }
}