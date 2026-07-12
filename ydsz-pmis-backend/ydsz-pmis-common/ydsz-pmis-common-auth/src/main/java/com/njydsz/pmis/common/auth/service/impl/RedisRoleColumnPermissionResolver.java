package com.njydsz.pmis.common.auth.service.impl;

import com.njydsz.pmis.common.auth.config.AuthProperties;
import com.njydsz.pmis.common.auth.model.ColumnScopeInfo;
import com.njydsz.pmis.common.auth.service.ColumnPermissionResolver;
import com.njydsz.pmis.common.auth.service.RbacUserInfoService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.redis.service.ops.RedisStringOps;
import com.njydsz.pmis.common.util.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 鍩轰簬 Redis 鐨勮鑹插垪鏉冮檺瑙ｆ瀽鍣ㄣ€?
 *
 * <p>鑱岃矗锛?
 * <ul>
 *   <li>鏍规嵁 accessToken 璇诲彇褰撳墠鐢ㄦ埛淇℃伅</li>
 *   <li>浠庣敤鎴蜂俊鎭В鏋?roleCode锛堟敮鎸佸瑙掕壊锛?/li>
 *   <li>鎸夎鑹茶鍙?role-col-key 骞跺悎骞跺垪鍙/鍙紪杈戣鍒?/li>
 *   <li>瀵瑰崟瑙掕壊鍒楁潈闄愮粨鏋滃仛鏈湴 Caffeine 缂撳瓨锛岄槻姝㈠唴瀛樻孩鍑?/li>
 * </ul>
 *
 * <p><b>缂撳瓨绛栫暐锛?/b>
 * <ul>
 *   <li>浣跨敤 Caffeine 鍋氭湰鍦扮紦瀛橈紝闃叉鍐呭瓨婧㈠嚭</li>
 *   <li>缂撳瓨鏃堕棿鐢?{@code roleColumnCacheSeconds} 閰嶇疆</li>
 *   <li>璁板綍缂撳瓨鍛戒腑鐜囩粺璁★紝鏀寔 JMX/Actuator 鐩戞帶</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see ColumnPermissionResolver
 * @see Caffeine
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
            return Caffeine.newBuilder().build();
        }
        return Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .removalListener((String key, ColumnScopeInfo value, RemovalCause cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("鍒楁潈闄愮紦瀛樻窐姹? roleCode={}, cause={}", key, cause);
                    }
                })
                .build();
    }

    /**
     * 瑙ｆ瀽褰撳墠鐢ㄦ埛鐨勫垪鏉冮檺淇℃伅銆?
     *
     * <p>浠庡綋鍓嶈姹備笂涓嬫枃鑾峰彇 token锛屽姞杞界敤鎴蜂俊鎭紝瑙ｆ瀽瑙掕壊缂栫爜锛?
     * 鐒跺悗鍚堝苟鎵€鏈夎鑹茬殑鍒楁潈闄愯鍒欍€?
     *
     * @return 鍒楁潈闄愪俊鎭紝鏃犳潈闄愭椂杩斿洖绌虹殑 {@link ColumnScopeInfo}
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
     * 鏍规嵁瑙掕壊缂栫爜闆嗗悎瑙ｆ瀽鍒楁潈闄愪俊鎭€?
     *
     * <p>閬嶅巻姣忎釜瑙掕壊缂栫爜锛屼粠缂撳瓨鎴?Redis 涓姞杞藉搴旂殑鍒楁潈闄愯鍒欙紝
     * 鍚堝苟鎵€鏈夎鑹茬殑鍙/鍙紪杈戝瓧娈甸泦鍚堛€?
     *
     * @param roleCodes 瑙掕壊缂栫爜闆嗗悎
     * @return 鍚堝苟鍚庣殑鍒楁潈闄愪俊鎭紝鏃犳潈闄愭椂杩斿洖绌虹殑 {@link ColumnScopeInfo}
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
            log.warn("瑙ｆ瀽 role-col-key 澶辫触锛歿}", json, e);
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
     * 浣挎寚瀹氳鑹茬殑鍒楁潈闄愮紦瀛樺け鏁堛€?
     *
     * @param roleCode 瑙掕壊缂栫爜
     */
    public void invalidate(String roleCode) {
        if (StringUtils.isNotBlank(roleCode)) {
            cache.invalidate(roleCode.trim());
        }
    }

    /**
     * 浣挎墍鏈夊垪鏉冮檺缂撳瓨澶辨晥銆?
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 鑾峰彇鍒楁潈闄愭湰鍦扮紦瀛樺疄渚嬨€?
     *
     * @return Caffeine 缂撳瓨瀹炰緥
     */
    public Cache<String, ColumnScopeInfo> getCache() {
        return cache;
    }
}