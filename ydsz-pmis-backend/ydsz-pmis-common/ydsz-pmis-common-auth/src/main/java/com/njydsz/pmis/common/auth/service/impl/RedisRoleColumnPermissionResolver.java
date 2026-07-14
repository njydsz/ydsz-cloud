package com.njydsz.pmis.common.auth.service.impl;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.common.cache.YdszCache;
import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheType;
import com.njydsz.pmis.common.cache.listener.RemovalCause;
import com.njydsz.pmis.common.auth.config.AuthProperties;
import com.njydsz.pmis.common.auth.model.ColumnScopeInfo;
import com.njydsz.pmis.common.auth.service.ColumnPermissionResolver;
import com.njydsz.pmis.common.auth.service.RbacUserInfoService;
import com.njydsz.pmis.common.redis.service.ops.RedisStringOps;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.util.string.StringUtils;

/**
 * 基于 Redis 的角色列权限解析器。
 *
 * <p>职责：
 * <ul>
 *   <li>根据 accessToken 读取当前用户信息</li>
 *   <li>从用户信息解析 roleCode（支持多角色）</li>
 *   <li>按角色读取 role-col-key 并合并列可见/可编辑规则</li>
 *   <li>对单角色列权限结果做本地 Caffeine 缓存，防止内存溢出</li>
 * </ul>
 *
 * <p><b>缓存策略：</b>
 * <ul>
 * <li>使用 ydsz-pmis-common-cache 做本地缓存，防止内存溢出</li>
     * <li>缓存时间由 {@code roleColumnCacheSeconds} 配置</li>
 *   <li>记录缓存命中率统计，支持 JMX/Actuator 监控</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see ColumnPermissionResolver
 * @see YdszCache
 */
public class RedisRoleColumnPermissionResolver implements ColumnPermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(RedisRoleColumnPermissionResolver.class);

    private final RedisStringOps redisStringOps;
    private final AuthProperties properties;
    private final RbacUserInfoService userInfoService;
    private final Cache<String, ColumnScopeInfo> cache;

    public RedisRoleColumnPermissionResolver(RedisStringOps redisStringOps, AuthProperties properties, RbacUserInfoService userInfoService) {
        this.redisStringOps = redisStringOps;
        this.properties = properties;
        this.userInfoService = userInfoService;
        this.cache = buildCache();
    }

    private Cache<String, ColumnScopeInfo> buildCache() {
        Integer ttlSeconds = properties.getRoleColumnCacheSeconds();
        if (ttlSeconds == null || ttlSeconds <= 0) {
            return YdszCache.<String, ColumnScopeInfo>newBuilder().build();
        }
        return YdszCache.<String, ColumnScopeInfo>newBuilder()
                .type(CacheType.TTL)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .removalListener((String key, ColumnScopeInfo value, RemovalCause cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("列权限缓存淘汰: roleCode={}, cause={}", key, cause);
                    }
                })
                .build();
    }

    /**
     * 解析当前用户的列权限信息。
     *
     * <p>从当前请求上下文获取 token，加载用户信息，解析角色编码，
     * 然后合并所有角色的列权限规则。
     *
     * @return 列权限信息，无权限时返回空的 {@link ColumnScopeInfo}
     */
    @Override
    public ColumnScopeInfo resolve() {
        String token = userInfoService.loadCurrentToken();
        if (StringUtils.isBlank(token)) {
            return ColumnScopeInfo.empty();
        }
        Map<String, Object> userInfo = userInfoService.loadUserInfoMap(token);
        if (userInfo == null || userInfo.isEmpty()) {
            return ColumnScopeInfo.empty();
        }
        Set<String> roleCodes = parseUserRoles(userInfo);
        if (roleCodes.isEmpty()) {
            return ColumnScopeInfo.empty();
        }
        return resolveByRoles(roleCodes);
    }

    /**
     * 根据角色编码集合解析列权限信息。
     *
     * <p>遍历每个角色编码，从缓存或 Redis 中加载对应的列权限规则，
     * 合并所有角色的可见/可编辑字段集合。
     *
     * @param roleCodes 角色编码集合
     * @return 合并后的列权限信息，无权限时返回空的 {@link ColumnScopeInfo}
     */
    public ColumnScopeInfo resolveByRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return ColumnScopeInfo.empty();
        }
        Map<String, Set<String>> visibleColumnsByTable = new LinkedHashMap<>();
        Map<String, Set<String>> editableColumnsByTable = new LinkedHashMap<>();
        for (String roleCode : roleCodes) {
            ColumnScopeInfo scopeInfo = cache.getIfPresent(roleCode);
            if (scopeInfo == null) {
                scopeInfo = loadOne(roleCode);
                if (scopeInfo != null && !scopeInfo.isEmpty()) {
                    cache.put(roleCode, scopeInfo);
                }
            }
            mergeRules(visibleColumnsByTable, scopeInfo == null ? null : scopeInfo.getVisibleColumnsByTable());
            mergeRules(editableColumnsByTable, scopeInfo == null ? null : scopeInfo.getEditableColumnsByTable());
        }
        if (visibleColumnsByTable.isEmpty() && editableColumnsByTable.isEmpty()) {
            return ColumnScopeInfo.empty();
        }
        return new ColumnScopeInfo(freezeRules(visibleColumnsByTable), freezeRules(editableColumnsByTable));
    }

    private ColumnScopeInfo loadOne(String roleCode) {
        if (StringUtils.isBlank(roleCode)) {
            return ColumnScopeInfo.empty();
        }
        String json = redisStringOps.get(String.format(properties.getRoleColKey(), roleCode.trim()), String.class);
        return parseScope(json);
    }

    private ColumnScopeInfo parseScope(String json) {
        if (StringUtils.isBlank(json)) {
            return ColumnScopeInfo.empty();
        }
        try {
            JsonNode node = JsonUtils.getMapper().readTree(json);
            if (node == null || node.isNull() || node.isEmpty()) {
                return ColumnScopeInfo.empty();
            }
            ObjectNode object = (ObjectNode) node;
            Map<String, Set<String>> visibleColumns = parseTableColumns(object, "visibleColumns", "visible");
            Map<String, Set<String>> editableColumns = parseTableColumns(object, "editableColumns", "editable");
            return new ColumnScopeInfo(visibleColumns, editableColumns);
        } catch (Exception e) {
            log.warn("解析 role-col-key 失败：{}", json, e);
            return ColumnScopeInfo.empty();
        }
    }

    private Map<String, Set<String>> parseTableColumns(ObjectNode object, String primaryKey, String fallbackKey) {
        JsonNode rule = object.get(primaryKey);
        if (rule == null && StringUtils.isNotBlank(fallbackKey)) {
            rule = object.get(fallbackKey);
        }
        if (rule == null || rule.isNull() || rule.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : rule.properties()) {
            String table = entry.getKey();
            if (StringUtils.isBlank(table)) {
                continue;
            }
            Set<String> columns = toColumnSet(entry.getValue());
            if (!columns.isEmpty()) {
                out.put(normalize(table), columns);
            }
        }
        return out;
    }

    private Set<String> toColumnSet(Object value) {
        if (value == null) {
            return Collections.emptySet();
        }
        if (value instanceof Iterable) {
            Set<String> out = new LinkedHashSet<>();
            for (Object item : (Iterable<?>) value) {
                String column = normalize(item == null ? null : String.valueOf(item));
                if (StringUtils.isNotBlank(column)) {
                    out.add(column);
                }
            }
            return out;
        }
        return Arrays.stream(String.valueOf(value).split(","))
                .map(this::normalize)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void mergeRules(Map<String, Set<String>> target, Map<String, Set<String>> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            if (StringUtils.isBlank(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            target.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
        }
    }

    private Map<String, Set<String>> freezeRules(Map<String, Set<String>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            out.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    private Set<String> parseUserRoles(Map<String, Object> userInfo) {
        Object value = userInfo.get(resolveRoleCodeField());
        if (value == null) {
            return Collections.emptySet();
        }
        return Arrays.stream(String.valueOf(value).split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveRoleCodeField() {
        String roleCodeField = properties.getRoleCodeField();
        return StringUtils.isBlank(roleCodeField) ? "roleCode" : roleCodeField.trim();
    }

    private String normalize(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    /**
     * 使指定角色的列权限缓存失效。
     *
     * @param roleCode 角色编码
     */
    public void invalidate(String roleCode) {
        if (StringUtils.isNotBlank(roleCode)) {
            cache.invalidate(roleCode.trim());
        }
    }

    /**
     * 使所有列权限缓存失效。
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 获取列权限本地缓存实例。
     *
     * @return 本地缓存实例
     */
    public Cache<String, ColumnScopeInfo> getCache() {
        return cache;
    }
}