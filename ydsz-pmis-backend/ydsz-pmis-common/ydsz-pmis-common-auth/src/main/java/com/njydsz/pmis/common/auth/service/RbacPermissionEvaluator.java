package com.njydsz.pmis.common.auth.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.auth.annotation.AuthMenuPermission;
import com.njydsz.pmis.common.auth.config.AuthProperties;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.auth.exception.PermissionDeniedException;
import com.njydsz.pmis.common.auth.exception.PermissionDeniedException.PermissionType;
import com.njydsz.pmis.common.auth.metrics.AuthMetricsCollector;
import com.njydsz.pmis.common.auth.model.RolePermissions;
import com.njydsz.pmis.common.auth.strategy.CacheKeyStrategy;
import com.njydsz.pmis.common.auth.strategy.DefaultCacheKeyStrategy;
import com.njydsz.pmis.common.auth.util.PermissionUtils;
import com.njydsz.pmis.common.cache.YdszCache;
import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheType;
import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.util.auth.RequestHolder;
import com.njydsz.pmis.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * RBAC 权限校验器。
 *
 * <p>职责边界：
 * <ul>
 *   <li>根据 accessToken 加载用户信息（Map）</li>
 *   <li>从用户信息中解析用户角色（固定字段 roleCode）</li>
 *   <li>根据角色加载权限集合（菜单/按钮/接口）</li>
 *   <li>按注解要求（AND/OR、权限类型）进行校验</li>
 * </ul>
 *
 * <p><b>注意：</b>本模块使用 ydsz-pmis-common-cache 做角色权限本地缓存（TTL 可配置，默认 30 分钟），
 * 当 Redis 不可用时降级到本地缓存。仅对通配符权限的正则 Pattern 做轻量缓存以避免重复编译。
 *
 * <p><b>异常说明：</b>
 * 校验失败时抛出 {@link PermissionDeniedException}，包含完整的权限上下文信息：
 * <ul>
 *   <li>userId：当前用户 ID</li>
 *   <li>userRoles：当前用户角色</li>
 *   <li>requiredPermissions：缺少的权限</li>
 *   <li>grantedPermissions：已有的权限</li>
 *   <li>resource：被访问的资源</li>
 *   <li>checkMode：校验模式（AND/OR）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see PermissionDeniedException
 */
@Slf4j
public class RbacPermissionEvaluator {

    private final AuthProperties properties;
    private final RbacUserInfoService userInfoService;
    private final RolePermissionLoader rolePermissionLoader;

    /**
     * 可选的指标采集器，由 AuthConfiguration 注入。为 null 时不采集指标（向后兼容）。
     */
    private AuthMetricsCollector metricsCollector;

    /**
     * 缓存 Key 生成策略，默认为 DefaultCacheKeyStrategy
     */
    private CacheKeyStrategy cacheKeyStrategy = new DefaultCacheKeyStrategy();

    /**
     * 标记 Redis 是否可用，供 AuthConfiguration 的健康检查更新。
     */
    private volatile boolean redisAvailable = true;

    /**
     * 角色权限缓存（TTL 可配置，默认 30 分钟）
     * 使用 Caffeine 缓存，自带 LRU 淘汰和过期清理
     */
    private final Cache<String, RolePermissions> rolePermissionsCache;

    /**
     * roleCode → cacheKey 的反向索引，用于按角色清理缓存。
     * 由于 cacheKey 使用 SHA-256 Hash，无法从 Key 反解角色，需维护此映射。
     */
    private final Map<String, Set<String>> roleToCacheKeyIndex = new ConcurrentHashMap<>();

    public RbacPermissionEvaluator(AuthProperties properties, RbacUserInfoService userInfoService, RolePermissionLoader rolePermissionLoader) {
        this.properties = properties;
        this.userInfoService = userInfoService;
        this.rolePermissionLoader = rolePermissionLoader;
        this.rolePermissionsCache = YdszCache.<String, RolePermissions>newBuilder()
                .type(CacheType.TTL)
                .maximumSize(1000)
                .expireAfterWrite(resolvePermissionCacheTtlSeconds(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * 使用 accessToken 加载用户信息。
     *
     * @param accessToken 请求 token（通常来自请求头 X-Access-Token）
     * @return 用户信息 Map
     */
    public Map<String, Object> loadUserInfo(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            throw BusinessException.builder().code(String.valueOf(HttpStatus.UNAUTHORIZED.value())).message("缺少访问令牌").build();
        }

        Map<String, Object> userInfo = userInfoService.loadUserInfoMap(accessToken);
        if (userInfo == null || userInfo.isEmpty()) {
            throw BusinessException.builder().code(String.valueOf(HttpStatus.UNAUTHORIZED.value())).message("访问令牌已过期，请重新登录").build();
        }
        return userInfo;
    }

    /**
     * 加载当前登录用户信息。
     *
     * <p>优先从请求级 ThreadLocal 缓存获取，缓存未命中时才从 Redis 加载，
     * 避免同一请求内多次调用导致反复 Redis 查询（原实现每次调用均走 Redis）。
     *
     * @return 用户信息 Map
     */
    public Map<String, Object> loadCurrentUserInfo() {
        Map<String, Object> cached = AuthContext.getCachedUserInfoMap();
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        Map<String, Object> userInfo = loadUserInfo(userInfoService.loadCurrentToken());
        AuthContext.setCachedUserInfoMap(userInfo);
        return userInfo;
    }

    /**
     * 校验是否满足注解要求的角色与权限。
     *
     * @param userInfo 用户信息 Map
     * @param required 目标方法/类上的 {@link AuthMenuPermission} 注解
     */
    public void validateMenu(Map<String, Object> userInfo, AuthMenuPermission required) {
        if (required == null) return;
        validateMenu0(userInfo, required.permissionCodes(), required.roleCodes(),
                required.type(), required.mode());
    }

    private void validateMenu0(Map<String, Object> userInfo, String[] permissionCodes, String[] roleCodes,
                                AuthMenuPermission.PermissionType type, AuthMenuPermission.Mode mode) {
        if (!properties.isEnabled()) return;
        Set<String> requiredRoles = arrayToSet(roleCodes);
        Set<String> requiredPerms = arrayToSet(permissionCodes);
        if (requiredRoles.isEmpty() && requiredPerms.isEmpty()) return;

        Set<String> userRoles = userInfo != null ? parseUserRoles(userInfo) : new HashSet<>();
        if (isSuperAdmin(userRoles)) return;

        PermissionType permType = mapToPermissionType(type);
        boolean orMode = mode == AuthMenuPermission.Mode.OR;

        if (!requiredRoles.isEmpty()) {
            validateRoles(userRoles, requiredRoles, orMode, permType);
        }
        if (!requiredPerms.isEmpty()) {
            RolePermissions rp = getPermissionsByRoleCodes(userRoles);
            Set<String> userPerms = selectPermissions(rp, type);
            validatePermissions(userPerms, requiredPerms, orMode, permType, userPerms);
        }
    }

    /**
     * 校验当前用户是否满足接口权限要求。
     *
     * @param apiCodes 需要校验的接口权限码数组
     * @param roleCodes 需要校验的角色编码数组
     * @param mode 校验模式（AND/OR）
     */
    public void validateApi(String[] apiCodes, String[] roleCodes, AuthApiPermission.Mode mode) {
        validateApi0(loadCurrentUserInfo(), apiCodes, roleCodes, mode);
    }

    /**
     * 校验当前用户是否满足菜单/按钮权限要求。
     *
     * @param permissionCodes 需要校验的权限码数组
     * @param roleCodes 需要校验的角色编码数组
     * @param type 权限类型（菜单/按钮）
     * @param mode 校验模式（AND/OR）
     */
    public void validateMenu(String[] permissionCodes, String[] roleCodes,
                            AuthMenuPermission.PermissionType type, AuthMenuPermission.Mode mode) {
        validateMenu0(loadCurrentUserInfo(), permissionCodes, roleCodes, type, mode);
    }

    /**
     * 校验用户是否拥有指定接口权限。
     *
     * @param userInfo 用户信息 Map
     * @param required 接口权限注解
     */
    public void validateApi(Map<String, Object> userInfo, AuthApiPermission required) {
        if (required == null) return;
        validateApi0(userInfo, required.apiCodes(), required.roleCodes(), required.mode());
    }

    private void validateApi0(Map<String, Object> userInfo, String[] apiCodes, String[] roleCodes, AuthApiPermission.Mode mode) {
        if (!properties.isEnabled()) return;
        Set<String> requiredRoles = arrayToSet(roleCodes);
        Set<String> requiredApis = arrayToSet(apiCodes);
        if (requiredRoles.isEmpty() && requiredApis.isEmpty()) return;

        Set<String> userRoles = userInfo != null ? parseUserRoles(userInfo) : new HashSet<>();
        if (isSuperAdmin(userRoles)) return;

        boolean orMode = mode == AuthApiPermission.Mode.OR;

        if (!requiredRoles.isEmpty()) {
            validateRoles(userRoles, requiredRoles, orMode, PermissionType.API);
        }
        if (!requiredApis.isEmpty()) {
            RolePermissions rp = getPermissionsByRoleCodes(userRoles);
            Set<String> grantedPerms = rp.getApiPermissions();
            validatePermissions(grantedPerms, requiredApis, orMode, PermissionType.API, grantedPerms);
        }
    }

    /**
     * 设置缓存 Key 生成策略。
     *
     * <p>支持自定义缓存 Key 生成规则，默认为 {@link DefaultCacheKeyStrategy}。
     *
     * @param cacheKeyStrategy 缓存 Key 生成策略
     */
    /**
     * 设置指标采集器（由 AuthConfiguration 注入）。
     *
     * @param metricsCollector 指标采集器，为 null 时不采集
     */
    public void setMetricsCollector(AuthMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    /**
     * 记录权限校验通过。
     */
    private void recordAllow(String permissionType) {
        if (metricsCollector != null) {
            metricsCollector.recordPermissionAllow(permissionType);
        }
    }

    /**
     * 记录权限校验拒绝。
     */
    private void recordDeny(String permissionType, String requiredPermissions, String resource) {
        if (metricsCollector != null) {
            metricsCollector.recordPermissionDeny(resolveUserId(), permissionType, requiredPermissions, resource);
        }
    }

    public void setCacheKeyStrategy(CacheKeyStrategy cacheKeyStrategy) {
        Objects.requireNonNull(cacheKeyStrategy, "cacheKeyStrategy cannot be null");
        this.cacheKeyStrategy = cacheKeyStrategy;
    }

    /**
     * 获取当前缓存 Key 生成策略。
     *
     * @return 缓存 Key 生成策略
     */
    public CacheKeyStrategy getCacheKeyStrategy() {
        return cacheKeyStrategy;
    }

    /**
     * 标记 Redis 是否可用。
     *
     * <p>由 AuthConfiguration 的定时健康检查调用。
     *
     * @param available Redis 是否可用
     */
    public void setRedisAvailable(boolean available) {
        if (this.redisAvailable != available) {
            this.redisAvailable = available;
            if (!available) {
                log.warn("[RbacPermissionEvaluator] Redis 不可用，已切换降级模式，fallbackPolicy={}",
                        properties.getFallbackPolicy());
            } else {
                log.info("[RbacPermissionEvaluator] Redis 已恢复，切换回正常模式");
            }
        }
    }

    /**
     * 判断 Redis 是否可用。
     *
     * @return Redis 是否可用
     */
    public boolean isRedisAvailable() {
        return redisAvailable;
    }

    /**
     * 清理内部缓存。
     */
    public void clearAllCaches() {
        PermissionUtils.clearPatternCache();
        rolePermissionsCache.invalidateAll();
        roleToCacheKeyIndex.clear();
    }

    /**
     * 销毁时清理缓存。
     */
    @PreDestroy
    public void destroy() {
        rolePermissionsCache.invalidateAll();
        log.info("[RbacPermissionEvaluator] 缓存已清理");
    }

    /**
     * 判断当前用户是否拥有指定权限。
     *
     * <p>合并用户所有角色的菜单/按钮/接口权限，判断是否包含指定权限码。
     * 支持通配符匹配（需启用 {@code wildcardEnabled}）。
     *
     * @param permission 权限码
     * @return 是否拥有该权限
     */
    public boolean hasPermission(String permission) {
        try {
            Map<String, Object> userInfo = loadCurrentUserInfo();
            Set<String> userRoles = parseUserRoles(userInfo);
            if (isSuperAdmin(userRoles)) {
                return true;
            }
            RolePermissions rp = getPermissionsByRoleCodes(userRoles);
            Set<String> allPerms = new HashSet<>();
            if (rp.getMenuPermissions() != null) allPerms.addAll(rp.getMenuPermissions());
            if (rp.getButtonPermissions() != null) allPerms.addAll(rp.getButtonPermissions());
            if (rp.getApiPermissions() != null) allPerms.addAll(rp.getApiPermissions());
            return hasPermission(allPerms, permission);
        } catch (Exception e) {
            log.error("[RbacPermissionEvaluator] 权限校验异常，Redis 连接失败导致权限检查失败, permission={}, fallbackPolicy={}",
                    permission, properties.getFallbackPolicy(), e);
            return false;
        }
    }

    private RolePermissions getPermissionsByRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return RolePermissions.empty();
        }

        String tenantId = resolveTenantId();
        String cacheKey = cacheKeyStrategy.generate(tenantId, roleCodes);
        RolePermissions cached = rolePermissionsCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        Set<String> menu = new HashSet<>();
        Set<String> button = new HashSet<>();
        Set<String> api = new HashSet<>();

        // 使用批量加载替代逐个加载，将 N 次 Redis 往返减少为 2 次（MGET）
        try {
            Map<String, RolePermissions> permissionsMap = rolePermissionLoader.loadByRoleCodes(roleCodes);
            for (RolePermissions single : permissionsMap.values()) {
                if (single == null) {
                    continue;
                }
                if (single.getMenuPermissions() != null) {
                    menu.addAll(single.getMenuPermissions());
                }
                if (single.getButtonPermissions() != null) {
                    button.addAll(single.getButtonPermissions());
                }
                if (single.getApiPermissions() != null) {
                    api.addAll(single.getApiPermissions());
                }
            }
        } catch (Exception e) {
            // Redis 不可用时降级：记录日志并继续，仅当 Redis 可用时才放入缓存避免缓存毒化
            log.warn("[RbacPermissionEvaluator] 批量加载角色权限失败，redisAvailable={}, fallbackPolicy={}: {}",
                    redisAvailable, properties.getFallbackPolicy(), e.getMessage());
        }

        RolePermissions result = new RolePermissions(
                Collections.unmodifiableSet(menu),
                Collections.unmodifiableSet(button),
                Collections.unmodifiableSet(api)
        );

        // 仅在 Redis 可用时放入缓存，避免 Redis 故障期间的空权限被缓存毒化
        if (redisAvailable) {
            rolePermissionsCache.put(cacheKey, result);
            // 维护 roleCode → cacheKey 反向索引，用于按角色清理缓存
            for (String roleCode : roleCodes) {
                roleToCacheKeyIndex.computeIfAbsent(roleCode, k -> ConcurrentHashMap.newKeySet())
                        .add(cacheKey);
            }
        }

        return result;
    }

    private String resolveTenantId() {
        // 优先从 ThreadLocal 缓存获取，避免同一次请求内多次 Redis 查询
        String cached = AuthContext.getTenantId();
        if (cached != null) {
            return cached;
        }
        try {
            // loadCurrentUserInfo() 已有请求级缓存，不会重复查 Redis
            Map<String, Object> userInfo = loadCurrentUserInfo();
            if (userInfo != null) {
                Object tenantId = userInfo.get("tenantId");
                if (tenantId != null) {
                    String tid = String.valueOf(tenantId);
                    AuthContext.setTenantId(tid);
                    return tid;
                }
            }
        } catch (Exception e) {
            log.debug("解析 tenantId 异常，将返回 null: {}", e.getMessage());
        }
        return null;
    }

    private long resolvePermissionCacheTtlSeconds() {
        Integer ttlSeconds = properties.getPermissionCacheTtlSeconds();
        if (ttlSeconds == null || ttlSeconds <= 0) {
            return 1800;
        }
        return ttlSeconds;
    }

    /**
     * 按角色清理缓存。
     *
     * <p>由于缓存 Key 使用 SHA-256 Hash，无法从 Key 反解角色，
     * 通过维护的 roleCode → cacheKey 反向索引进行精确清理。
     */
    public void clearCachesByRoleCodes(String csvRoleCodes) {
        PermissionUtils.clearPatternCache();
        if (csvRoleCodes != null && !csvRoleCodes.isEmpty()) {
            Set<String> roleCodes = PermissionUtils.splitCsv(csvRoleCodes);
            for (String roleCode : roleCodes) {
                Set<String> cacheKeys = roleToCacheKeyIndex.remove(roleCode);
                if (cacheKeys != null) {
                    for (String cacheKey : cacheKeys) {
                        rolePermissionsCache.invalidate(cacheKey);
                    }
                }
            }
        } else {
            rolePermissionsCache.invalidateAll();
            roleToCacheKeyIndex.clear();
        }
    }

    private Set<String> selectPermissions(RolePermissions rolePermissions, AuthMenuPermission.PermissionType type) {
        if (rolePermissions == null) {
            return Collections.emptySet();
        }
        switch (type) {
            case MENU:
                return rolePermissions.getMenuPermissions();
            case BUTTON:
            default:
                return rolePermissions.getButtonPermissions();
        }
    }

    private boolean isSuperAdmin(Set<String> userRoles) {
        return PermissionUtils.isSuperAdmin(userRoles, properties.getIgnoreRoles());
    }

    private void validateRoles(Set<String> userRoles, Set<String> requiredRoles, boolean orMode, PermissionType type) {
        boolean ok;
        if (orMode) {
            ok = userRoles.stream().anyMatch(requiredRoles::contains);
        } else {
            ok = userRoles.containsAll(requiredRoles);
        }
        if (!ok) {
            recordDeny(type.name(), requiredRoles.toString(), resolveCurrentResource());
            throw PermissionDeniedException.denied()
                    .userId(resolveUserId())
                    .userRoles(userRoles)
                    .requiredPermissions(requiredRoles)
                    .permissionType(type)
                    .resource(resolveCurrentResource())
                    .checkMode(orMode ? "OR" : "AND")
                    .build();
        }
        recordAllow(type.name());
    }

    private void validatePermissions(Set<String> grantedPerms, Set<String> requiredPerms,
                                      boolean orMode, PermissionType type, Set<String> userGrantedPerms) {
        Set<String> matchedPermissions = new HashSet<>();
        Set<String> missingPermissions = new HashSet<>();

        for (String required : requiredPerms) {
            boolean found = hasPermission(grantedPerms, required);
            if (found) {
                matchedPermissions.add(required);
            } else {
                missingPermissions.add(required);
            }
        }

        boolean ok;
        if (orMode) {
            ok = !matchedPermissions.isEmpty();
        } else {
            ok = missingPermissions.isEmpty();
        }

        if (!ok) {
            recordDeny(type.name(), missingPermissions.toString(), resolveCurrentResource());
            throw PermissionDeniedException.denied()
                    .userId(resolveUserId())
                    .userRoles(parseUserRoles(loadCurrentUserInfo()))
                    .requiredPermissions(requiredPerms)
                    .grantedPermissions(userGrantedPerms)
                    .permissionType(type)
                    .resource(resolveCurrentResource())
                    .checkMode(orMode ? "OR" : "AND")
                    .build();
        }
        recordAllow(type.name());
    }

    private boolean hasPermission(Set<String> granted, String required) {
        return PermissionUtils.hasPermission(granted, required, properties.isWildcardEnabled());
    }

    private Set<String> parseUserRoles(Map<String, Object> userInfo) {
        if (userInfo == null) {
            return Collections.emptySet();
        }
        Object v = userInfo.get(resolveRoleCodeField());
        if (v == null) {
            return Collections.emptySet();
        }
        return PermissionUtils.splitCsv(String.valueOf(v));
    }

    private String resolveRoleCodeField() {
        String roleCodeField = properties.getRoleCodeField();
        if (StringUtils.isBlank(roleCodeField)) {
            return "roleCode";
        }
        return roleCodeField.trim();
    }

    private String resolveUserId() {
        try {
            Map<String, Object> userInfo = loadCurrentUserInfo();
            if (userInfo != null) {
                Object userId = userInfo.get("userId");
                if (userId != null) {
                    return String.valueOf(userId);
                }
            }
        } catch (Exception e) {
            log.debug("[RbacPermissionEvaluator] 解析当前用户ID失败: {}", e.getMessage());
        }
        return null;
    }

    private String resolveCurrentResource() {
        try {
            Object request = RequestHolder.getCurrentRequest();
            if (request instanceof HttpServletRequest) {
                return ((HttpServletRequest) request).getRequestURI();
            }
        } catch (Exception e) {
            log.debug("[RbacPermissionEvaluator] 解析当前资源路径失败: {}", e.getMessage());
        }
        return null;
    }

    private Set<String> arrayToSet(String[] arr) {
        if (arr == null || arr.length == 0) {
            return Collections.emptySet();
        }
        return Arrays.stream(arr)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private PermissionType mapToPermissionType(AuthMenuPermission.PermissionType type) {
        if (type == null) {
            return PermissionType.MENU;
        }
        switch (type) {
            case MENU:
                return PermissionType.MENU;
            case BUTTON:
                return PermissionType.BUTTON;
            default:
                return PermissionType.MENU;
        }
    }
}