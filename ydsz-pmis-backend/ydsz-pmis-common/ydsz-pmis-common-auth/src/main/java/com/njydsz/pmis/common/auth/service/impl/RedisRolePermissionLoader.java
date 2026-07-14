package com.njydsz.pmis.common.auth.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import com.njydsz.pmis.common.auth.cache.LocalPermissionCache;
import com.njydsz.pmis.common.auth.config.AuthProperties;
import com.njydsz.pmis.common.auth.event.PermissionChangeNotifier;
import com.njydsz.pmis.common.auth.model.RolePermissions;
import com.njydsz.pmis.common.auth.service.RolePermissionLoader;
import com.njydsz.pmis.common.cache.YdszCache;
import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheType;
import com.njydsz.pmis.common.cache.listener.RemovalCause;
import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.common.redis.service.ops.RedisStringOps;
import com.njydsz.pmis.common.util.string.StringUtils;

/**
 * 基于 Redis 的角色权限加载器实现。
 *
 * <p>从 Redis 中加载角色的菜单、按钮、接口权限信息。
 *
 * <p><b>数据来源：</b>
 * <ul>
 *   <li>role-menu-key：存储菜单/按钮权限，JSON 格式包含 menus、buttons、apis 字段</li>
 *   <li>role-api-key：存储接口权限，可为 JSON 对象或字符串数组</li>
 * </ul>
 *
 * <p><b>缓存策略：</b>
 * <ul>
 * <li>使用 ydsz-pmis-common-cache 做本地缓存，防止内存溢出</li>
     * <li>缓存时间由 {@code rolePermissionCacheSeconds} 配置</li>
 *   <li>缓存失效后自动重新加载</li>
 *   <li>记录缓存命中率统计，支持 JMX/Actuator 监控</li>
 *   <li>支持 Redis Pub/Sub 跨实例缓存失效通知</li>
 *   <li>支持定时刷新缓存，保证数据最终一致性</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see RolePermissionLoader
 * @see YdszCache
 */
public class RedisRolePermissionLoader implements RolePermissionLoader {

    private static final Logger log = LoggerFactory.getLogger(RedisRolePermissionLoader.class);

    private final RedisStringOps redisStringOps;
    private final AuthProperties properties;
    private final Cache<String, RolePermissions> cache;
    private final PermissionChangeNotifier notifier;
    private final LocalPermissionCache<RolePermissions> localCache;

    /**
     * 标记 Redis 是否可用，初始值为 true。
     */
    private volatile boolean redisAvailable = true;

    public RedisRolePermissionLoader(RedisStringOps redisStringOps, AuthProperties properties,
                                     PermissionChangeNotifier notifier) {
        this(redisStringOps, properties, notifier, null);
    }

    public RedisRolePermissionLoader(RedisStringOps redisStringOps, AuthProperties properties,
                                     PermissionChangeNotifier notifier,
                                     LocalPermissionCache<RolePermissions> localCache) {
        this.redisStringOps = redisStringOps;
        this.properties = properties;
        this.cache = buildCache();
        this.notifier = notifier;
        this.localCache = localCache;
    }

    /**
     * 标记 Redis 是否可用。
     *
     * @param available Redis 是否可用
     */
    public void setRedisAvailable(boolean available) {
        if (this.redisAvailable != available) {
            this.redisAvailable = available;
            if (!available) {
                log.warn("【权限模块】Redis 不可用，RedisRolePermissionLoader 已降级到本地缓存");
            } else {
                log.info("【权限模块】Redis 已恢复，RedisRolePermissionLoader 继续使用 Redis 加载权限");
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

    private Cache<String, RolePermissions> buildCache() {
        Integer ttlSeconds = properties.getRolePermissionCacheSeconds();
        if (ttlSeconds == null || ttlSeconds <= 0) {
            return YdszCache.<String, RolePermissions>newBuilder().build();
        }
        return YdszCache.<String, RolePermissions>newBuilder()
                .type(CacheType.TTL)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .removalListener((String key, RolePermissions value, RemovalCause cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("角色权限缓存淘汰: roleCode={}, cause={}", key, cause);
                    }
                })
                .build();
    }

    /**
     * 根据角色编码加载权限集合。
     *
     * <p>优先从本地缓存获取，缓存未命中时从 Redis 加载菜单/按钮/接口权限，
     * 加载成功后写入缓存并发布角色变更事件。
     * <p>当 Redis 不可用时，自动降级到 LocalPermissionCache（如果已配置）。
     *
     * @param roleCode 角色编码
     * @return 角色权限集合，角色编码为空时返回空的 {@link RolePermissions}
     */
    @Override
    public RolePermissions loadByRoleCode(String roleCode) {
        if (StringUtils.isBlank(roleCode)) {
            return RolePermissions.empty();
        }
        String role = roleCode.trim();
        RolePermissions cached = cache.getIfPresent(role);
        if (cached != null) {
            return cached;
        }

        // 如果 Redis 不可用，尝试从 LocalPermissionCache 降级获取
        if (!redisAvailable) {
            if (localCache != null) {
                RolePermissions localValue = localCache.get(role);
                if (localValue != null) {
                    log.warn("【权限模块】Redis 不可用，从本地缓存获取角色权限: roleCode={}", roleCode);
                    return localValue;
                }
            }
            log.warn("【权限模块】Redis 不可用且本地缓存无数据，返回空权限: roleCode={}", roleCode);
            return RolePermissions.empty();
        }

        try {
            Set<String> menuPerms = new HashSet<>();
            Set<String> buttonPerms = new HashSet<>();
            Set<String> apiPerms = new HashSet<>();

            loadFromRoleMenuKey(role, menuPerms, buttonPerms);
            loadFromRoleApiKey(role, apiPerms);

            RolePermissions loaded = new RolePermissions(
                    Collections.unmodifiableSet(menuPerms),
                    Collections.unmodifiableSet(buttonPerms),
                    Collections.unmodifiableSet(apiPerms)
            );
            cache.put(role, loaded);

            // 同时写入 LocalPermissionCache，为后续 Redis 降级时提供兜底数据
            if (localCache != null) {
                localCache.put(role, loaded);
            }

            publishRoleChangedEvent(role);
            return loaded;
        } catch (Exception e) {
            // Redis 异常时标记不可用，并降级到本地缓存
            log.error("【权限模块】Redis 加载权限异常，降级到本地缓存: roleCode={}, error={}", roleCode, e.getMessage(), e);
            redisAvailable = false;
            if (localCache != null) {
                RolePermissions localValue = localCache.get(role);
                if (localValue != null) {
                    log.warn("【权限模块】降级成功，从本地缓存返回角色权限: roleCode={}", roleCode);
                    return localValue;
                }
            }
            return RolePermissions.empty();
        }
    }

    /**
     * 批量加载多个角色的权限集合。
     *
     * <p>使用 Redis MGET 批量获取所有角色的 menu-key 和 api-key，
     * 将 N 个角色的 2N 次 Redis 往返减少为 2 次，大幅降低网络延迟。
     *
     * <p>处理流程：
     * <ol>
     *   <li>先检查 Caffeine 本地缓存，筛选出未缓存的角色</li>
     *   <li>对未缓存的角色，构建 menu-keys 和 api-keys 列表</li>
     *   <li>MGET 批量获取所有 menu-keys（1 次往返）</li>
     *   <li>MGET 批量获取所有 api-keys（1 次往返）</li>
     *   <li>解析每个角色的 JSON 数据，构建 RolePermissions</li>
     *   <li>写入 Caffeine 缓存和 LocalPermissionCache</li>
     * </ol>
     *
     * @param roleCodes 角色编码集合
     * @return 角色编码 → 权限集合的映射
     */
    @Override
    public Map<String, RolePermissions> loadByRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, RolePermissions> result = new HashMap<>(roleCodes.size());
        List<String> uncachedRoles = new ArrayList<>(roleCodes.size());

        // 1. 先检查本地缓存
        for (String roleCode : roleCodes) {
            if (StringUtils.isBlank(roleCode)) {
                continue;
            }
            String role = roleCode.trim();
            RolePermissions cached = cache.getIfPresent(role);
            if (cached != null) {
                result.put(role, cached);
            } else {
                uncachedRoles.add(role);
            }
        }

        if (uncachedRoles.isEmpty()) {
            return result;
        }

        // Redis 不可用时，逐个降级加载
        if (!redisAvailable) {
            for (String role : uncachedRoles) {
                result.put(role, loadByRoleCode(role));
            }
            return result;
        }

        // 2. 构建 Redis Key 列表
        int uncachedCount = uncachedRoles.size();
        List<String> menuKeys = new ArrayList<>(uncachedCount);
        List<String> apiKeys = new ArrayList<>(uncachedCount);
        for (String role : uncachedRoles) {
            menuKeys.add(String.format(properties.getRoleMenuKey(), role));
            apiKeys.add(String.format(properties.getRoleApiKey(), role));
        }

        try {
            // 3. MGET 批量获取 menu 数据和 api 数据（2 次 Redis 往返）
            List<String> menuDataList = redisStringOps.mget(menuKeys);
            List<String> apiDataList = redisStringOps.mget(apiKeys);

            // 4. 解析每个角色的权限数据
            for (int i = 0; i < uncachedCount; i++) {
                String role = uncachedRoles.get(i);
                String menuData = (i < menuDataList.size()) ? menuDataList.get(i) : null;
                String apiData = (i < apiDataList.size()) ? apiDataList.get(i) : null;

                Set<String> menuPerms = new HashSet<>();
                Set<String> buttonPerms = new HashSet<>();
                Set<String> apiPerms = new HashSet<>();

                // 解析 menu-key 数据（menus + buttons）
                JsonNode menuNode = parseJsonSafe(menuData, role);
                if (menuNode != null && !menuNode.isMissing()) {
                    readStringArray(menuNode.get("menus"), menuPerms);
                    readStringArray(menuNode.get("buttons"), buttonPerms);
                }

                // 解析 api-key 数据
                if (StringUtils.isNotBlank(apiData)) {
                    readApiPermissionsSafe(apiData, apiPerms);
                } else if (menuNode != null && !menuNode.isMissing()) {
                    // api-key 不存在时，从 menu-key 的 apis 字段降级获取
                    readStringArray(menuNode.get("apis"), apiPerms);
                }

                RolePermissions loaded = new RolePermissions(
                        Collections.unmodifiableSet(menuPerms),
                        Collections.unmodifiableSet(buttonPerms),
                        Collections.unmodifiableSet(apiPerms)
                );
                cache.put(role, loaded);
                if (localCache != null) {
                    localCache.put(role, loaded);
                }
                result.put(role, loaded);
            }

            // 批量发布角色变更事件
            for (String role : uncachedRoles) {
                publishRoleChangedEvent(role);
            }
        } catch (Exception e) {
            log.error("【权限模块】批量加载角色权限异常，降级到逐个加载: error={}", e.getMessage(), e);
            redisAvailable = false;
            // 降级到逐个加载
            for (String role : uncachedRoles) {
                if (!result.containsKey(role)) {
                    result.put(role, loadByRoleCode(role));
                }
            }
        }

        return result;
    }

    /**
     * 安全解析 JSON，解析失败返回 null
     */
    private JsonNode parseJsonSafe(String jsonData, String roleCode) {
        if (StringUtils.isBlank(jsonData)) {
            return null;
        }
        try {
            return YdszJson.readTree(jsonData);
        } catch (Exception e) {
            log.warn("【权限模块】解析角色权限 JSON 失败: roleCode={}, error={}", roleCode, e.getMessage());
            return null;
        }
    }

    /**
     * 安全解析 API 权限数据，解析失败时记录日志但不抛出异常
     */
    private void readApiPermissionsSafe(String apiData, Set<String> apiPerms) {
        try {
            JsonNode parsed = YdszJson.readTree(apiData);
            if (parsed != null && parsed.isObject()) {
                if (!parsed.isMissing()) {
                    readStringArray(parsed.get("apis"), apiPerms);
                    return;
                }
            }
            if (parsed != null && parsed.isArray()) {
                if (!parsed.isMissing()) {
                    readStringArray(parsed, apiPerms);
                }
            }
        } catch (Exception e) {
            log.debug("[RedisRolePermissionLoader] 批量解析 API 权限数据失败: {}", e.getMessage());
        }
    }

    private void loadFromRoleMenuKey(String roleCode, Set<String> menuPerms, Set<String> buttonPerms) {
        try {
            String menuData = redisStringOps.get(String.format(properties.getRoleMenuKey(), roleCode), String.class);
            if (StringUtils.isBlank(menuData)) {
                return;
            }
            JsonNode obj = YdszJson.readTree(menuData);
            if (obj != null && !obj.isMissing()) {
                readStringArray(obj.get("menus"), menuPerms);
                readStringArray(obj.get("buttons"), buttonPerms);
                return;
            }
            throw BusinessException.builder().code(String.valueOf(HttpStatus.FORBIDDEN.value())).message("角色菜单/按钮权限数据格式错误").build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("加载角色菜单权限失败：roleCode={}, error={}", roleCode, e.getMessage(), e);
            throw BusinessException.builder().code(String.valueOf(HttpStatus.FORBIDDEN.value())).message("权限加载失败").build();
        }
    }

    private void loadFromRoleApiKey(String roleCode, Set<String> apiPerms) {
        try {
            String apiData = redisStringOps.get(String.format(properties.getRoleApiKey(), roleCode), String.class);
            if (StringUtils.isNotBlank(apiData)) {
                readApiPermissions(apiData, apiPerms, "角色接口权限数据格式错误");
                return;
            }
            String menuData = redisStringOps.get(String.format(properties.getRoleMenuKey(), roleCode), String.class);
            if (StringUtils.isBlank(menuData)) {
                return;
            }
            JsonNode obj = YdszJson.readTree(menuData);
            if (obj != null && !obj.isMissing()) {
                readStringArray(obj.get("apis"), apiPerms);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("加载角色接口权限失败：roleCode={}, error={}", roleCode, e.getMessage(), e);
            throw BusinessException.builder().code(String.valueOf(HttpStatus.FORBIDDEN.value())).message("权限加载失败").build();
        }
    }

    private void readApiPermissions(String apiData, Set<String> apiPerms, String errorMessage) {
        try {
            JsonNode parsed = YdszJson.readTree(apiData);
            if (parsed != null && parsed.isObject()) {
                if (!parsed.isMissing()) {
                    readStringArray(parsed.get("apis"), apiPerms);
                    return;
                }
            }
            if (parsed != null && parsed.isArray()) {
                if (!parsed.isMissing()) {
                    readStringArray(parsed, apiPerms);
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("[RedisRolePermissionLoader] 解析权限数据失败，将抛出异常: {}", e.getMessage());
        }
        throw BusinessException.builder().code(String.valueOf(HttpStatus.FORBIDDEN.value())).message(errorMessage).build();
    }

    private void readStringArray(JsonNode value, Set<String> target) {
        if (value == null || !value.isArray()) {
            return;
        }
        for (JsonNode item : value) {
            String v = item.asText("").trim();
            if (StringUtils.isNotBlank(v)) {
                target.add(v);
            }
        }
    }

    /**
     * 使指定角色的权限缓存失效。
     *
     * @param roleCode 角色编码
     */
    public void invalidate(String roleCode) {
        if (StringUtils.isNotBlank(roleCode)) {
            cache.invalidate(roleCode.trim());
        }
    }

    /**
     * 使所有权限缓存失效。
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 获取权限本地缓存实例。
     *
     * @return 本地缓存实例
     */
    public Cache<String, RolePermissions> getCache() {
        return cache;
    }

    private void publishRoleChangedEvent(String roleCode) {
        if (notifier != null) {
            notifier.notifyRoleChanged(roleCode);
        }
    }
}