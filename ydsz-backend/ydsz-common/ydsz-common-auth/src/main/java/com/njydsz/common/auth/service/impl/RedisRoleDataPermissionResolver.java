package com.njydsz.common.auth.service.impl;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.model.DataScopeInfo;
import com.njydsz.common.auth.service.DataPermissionResolver;
import com.njydsz.common.auth.service.RbacUserInfoService;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.cache.listener.RemovalCause;
import com.njydsz.common.core.enums.DataScopeType;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.string.StringUtils;

/**
 * 基于 Redis 的行级数据权限解析器实现。
 *
 * <p>从 Redis role-row-key 中加载角色的数据权限范围信息。
 *
 * <p><b>数据权限维度：</b>
 * <ul>
 *   <li>租户维度（TENANT）：按租户隔离数据</li>
 *   <li>集团维度（GROUP）：可访问集团下所有公司数据</li>
 *   <li>公司维度（COMPANY）：可访问公司及下属部门数据</li>
 *   <li>部门维度（DEPT）：可访问本部门及下级部门数据</li>
 *   <li>用户维度（USER）：仅可访问自己的数据</li>
 *   <li>项目维度（PROJECT）：可访问有权限的项目数据</li>
 *   <li>区域维度（REGION）：可访问有权限的区域数据</li>
 * </ul>
 *
 * <p><b>多角色合并策略：</b>
 * <ul>
 *   <li>数据范围 ID（companyIds/deptIds 等）：取并集</li>
 *   <li>scope：取优先级最高的</li>
 *   <li>tenantId/userId：取第一个非空值</li>
 * </ul>
 *
 * <p><b>缓存策略：</b>
 * <ul>
 * <li>使用 ydsz-common-cache 做本地缓存，防止内存溢出</li>
     * <li>缓存时间由 {@code roleDataCacheSeconds} 配置</li>
 *   <li>记录缓存命中率统计，支持 JMX/Actuator 监控</li>
 * </ul>
 *
 * @since 1.0.0
 * 
 * @see DataPermissionResolver
 * @see DataScopeInfo
 * @see YdszCache
 */
public class RedisRoleDataPermissionResolver implements DataPermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(RedisRoleDataPermissionResolver.class);

    private final RedisStringOps redisStringOps;
    private final AuthProperties properties;
    private final RbacUserInfoService userInfoService;
    private final Cache<String, DataScopeInfo> cache;

    public RedisRoleDataPermissionResolver(RedisStringOps redisStringOps, AuthProperties properties, RbacUserInfoService userInfoService) {
        this.redisStringOps = redisStringOps;
        this.properties = properties;
        this.userInfoService = userInfoService;
        this.cache = buildCache();
    }

    private Cache<String, DataScopeInfo> buildCache() {
        Integer ttlSeconds = properties.getRoleDataCacheSeconds();
        if (ttlSeconds == null || ttlSeconds <= 0) {
            return YdszCache.<String, DataScopeInfo>newBuilder().build();
        }
        return YdszCache.<String, DataScopeInfo>newBuilder()
                .type(CacheType.TTL)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .removalListener((String key, DataScopeInfo value, RemovalCause cause) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("数据权限缓存淘汰: roleCode={}, cause={}", key, cause);
                    }
                })
                .build();
    }

    /**
     * 解析当前用户的数据权限范围。
     *
     * <p>从当前请求上下文获取 token，加载用户信息，解析角色编码，
     * 然后合并所有角色的数据权限范围。
     *
     * @return 数据权限范围信息，无权限时返回空的 {@link DataScopeInfo}
     */
    @Override
    public DataScopeInfo resolve() {
        String token = userInfoService.loadCurrentToken();
        if (StringUtils.isBlank(token)) {
            return DataScopeInfo.empty();
        }
        Map<String, Object> userInfo = userInfoService.loadUserInfoMap(token);
        if (userInfo == null || userInfo.isEmpty()) {
            return DataScopeInfo.empty();
        }
        Set<String> roles = parseUserRoles(userInfo);
        if (roles.isEmpty()) {
            return DataScopeInfo.empty();
        }
        return resolveByRoles(roles);
    }

    /**
     * 根据用户信息 Map 解析数据权限范围。
     *
     * <p>从用户信息中提取角色编码，然后合并所有角色的数据权限范围。
     *
     * @param userInfo 用户信息 Map
     * @return 数据权限范围信息，无角色时返回空的 {@link DataScopeInfo}
     */
    public DataScopeInfo resolveByUserInfo(Map<String, Object> userInfo) {
        Set<String> roles = parseUserRoles(userInfo);
        if (roles.isEmpty()) {
            return DataScopeInfo.empty();
        }
        return resolveByRoles(roles);
    }

    /**
     * 根据角色编码集合解析数据权限范围。
     *
     * <p>遍历每个角色编码，从缓存或 Redis 中加载对应的数据权限范围，
     * 合并所有角色的数据范围（ID 取并集，scope 取优先级最高的）。
     *
     * @param roleCodes 角色编码集合
     * @return 合并后的数据权限范围信息
     */
    public DataScopeInfo resolveByRoles(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return DataScopeInfo.empty();
        }
        List<DataScopeInfo> all = new ArrayList<>();
        // 分离缓存命中和未命中的角色
        List<String> uncachedRoles = new ArrayList<>(roleCodes.size());
        for (String role : roleCodes) {
            DataScopeInfo cached = cache.getIfPresent(role);
            if (cached != null) {
                all.add(cached);
            } else {
                uncachedRoles.add(role);
            }
        }
        // 使用 MGET 批量加载未命中的角色数据
        if (!uncachedRoles.isEmpty()) {
            try {
                Map<String, DataScopeInfo> loaded = loadByRolesMget(new java.util.LinkedHashSet<>(uncachedRoles));
                for (Map.Entry<String, DataScopeInfo> entry : loaded.entrySet()) {
                    if (entry.getValue() != null) {
                        cache.put(entry.getKey(), entry.getValue());
                        all.add(entry.getValue());
                    }
                }
            } catch (Exception e) {
                log.warn("批量加载数据权限失败，降级到逐个加载: {}", e.getMessage());
                for (String role : uncachedRoles) {
                    DataScopeInfo loaded = loadOne(role);
                    if (loaded != null) {
                        cache.put(role, loaded);
                        all.add(loaded);
                    }
                }
            }
        }
        if (all.isEmpty()) {
            return DataScopeInfo.empty();
        }
        return mergeDataScopeInfoList(all);
    }

    /**
     * 使用 Redis MGET 批量加载多个角色的数据权限范围。
     *
     * <p>相比逐个加载，批量加载可减少 Redis 网络往返次数。
     *
     * @param roleCodes 角色编码集合
     * @return 角色编码到数据权限范围的映射
     */
    public Map<String, DataScopeInfo> loadByRolesMget(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> keys = roleCodes.stream()
                .map(r -> String.format(properties.getRoleRowKey(), r))
                .collect(Collectors.toList());
        List<String> jsonList = keys.size() > 1
                ? redisStringOps.mget(keys)
                : Collections.singletonList(redisStringOps.get(keys.get(0), String.class));
        Map<String, DataScopeInfo> result = new LinkedHashMap<>();
        Iterator<String> itRole = roleCodes.iterator();
        Iterator<String> itJson = jsonList.iterator();
        while (itRole.hasNext()) {
            String role = itRole.next();
            String json = itJson.next();
            DataScopeInfo info = parseOneJson(json);
            if (info != null) {
                result.put(role, info);
            }
        }
        return result;
    }

    private DataScopeInfo loadOne(String roleCode) {
        return parseOneJson(redisStringOps.get(String.format(properties.getRoleRowKey(), roleCode), String.class));
    }

    private DataScopeInfo parseOneJson(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            JsonNode node = YdszJson.readTree(json);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isObject()) {
                return parseObject((ObjectNode) node);
            }
            if (node.isArray()) {
                return parseArray((ArrayNode) node);
            }
        } catch (Exception e) {
            log.warn("解析 role-row-key 失败：{}", json, e);
        }
        return null;
    }

    private DataScopeInfo parseObject(ObjectNode obj) {
        DataScopeType scope = parseScope(obj.has("scope") ? obj.get("scope").asText(null) : null);
        String tenantId = trimToNull(obj.has("tenantId") ? obj.get("tenantId").asText(null) : null);
        String userId = trimToNull(obj.has("userId") ? obj.get("userId").asText(null) : null);
        Set<String> companies = toStringSet(obj.get("companyIds"));
        Set<String> depts = toStringSet(obj.get("deptIds"));
        Set<String> projects = toStringSet(obj.get("projectIds"));
        Set<String> regions = toStringSet(obj.get("regionIds"));
        String customSqlCondition = trimToNull(obj.has("customSqlCondition") ? obj.get("customSqlCondition").asText(null) : null);
        String customSqlConditionTemplate = trimToNull(obj.has("customSqlConditionTemplate") ? obj.get("customSqlConditionTemplate").asText(null) : null);
        return new DataScopeInfo(scope, tenantId, userId, companies, depts, projects, regions,
                customSqlCondition, customSqlConditionTemplate);
    }

    private DataScopeInfo parseArray(ArrayNode arr) {
        Set<String> companies = new HashSet<>();
        Set<String> depts = new HashSet<>();
        Set<String> projects = new HashSet<>();
        Set<String> regions = new HashSet<>();
        DataScopeType maxScope = null;
        String tenantId = null;
        String userId = null;
        StringBuilder customSqlConditions = new StringBuilder();
        Iterator<JsonNode> elements = arr.elements();
        while (elements.hasNext()) {
            JsonNode element = elements.next();
            if (element == null || element.isNull()) {
                continue;
            }
            ObjectNode o = (ObjectNode) element;
            companies.addAll(toStringSet(o.get("companyIds")));
            depts.addAll(toStringSet(o.get("deptIds")));
            projects.addAll(toStringSet(o.get("projectIds")));
            regions.addAll(toStringSet(o.get("regionIds")));
            if (tenantId == null) {
                tenantId = trimToNull(o.has("tenantId") ? o.get("tenantId").asText(null) : null);
            }
            if (userId == null) {
                userId = trimToNull(o.has("userId") ? o.get("userId").asText(null) : null);
            }
            maxScope = DataScopeType.max(maxScope, parseScope(o.has("scope") ? o.get("scope").asText(null) : null));
            String customCondition = trimToNull(o.has("customSqlCondition") ? o.get("customSqlCondition").asText(null) : null);
            if (customCondition != null && !customCondition.isEmpty()) {
                if (customSqlConditions.length() > 0) {
                    customSqlConditions.append(" OR ");
                }
                customSqlConditions.append("(").append(customCondition).append(")");
            }
        }
        String mergedCustomCondition = customSqlConditions.length() > 0
                ? customSqlConditions.toString() : null;
        return new DataScopeInfo(maxScope, tenantId, userId, companies, depts, projects, regions,
                mergedCustomCondition, null);
    }

    private DataScopeInfo mergeDataScopeInfoList(List<DataScopeInfo> all) {
        Set<String> companies = new HashSet<>();
        Set<String> depts = new HashSet<>();
        Set<String> projects = new HashSet<>();
        Set<String> regions = new HashSet<>();
        DataScopeType maxScope = null;
        String tenantId = null;
        String userId = null;
        StringBuilder customSqlConditions = new StringBuilder();
        for (DataScopeInfo info : all) {
            if (info.getCompanyIds() != null) {
                companies.addAll(info.getCompanyIds());
            }
            if (info.getDeptIds() != null) {
                depts.addAll(info.getDeptIds());
            }
            if (info.getProjectIds() != null) {
                projects.addAll(info.getProjectIds());
            }
            if (info.getRegionIds() != null) {
                regions.addAll(info.getRegionIds());
            }
            if (tenantId == null) {
                tenantId = trimToNull(info.getTenantId());
            }
            if (userId == null) {
                userId = trimToNull(info.getUserId());
            }
            maxScope = DataScopeType.max(maxScope, info.getScope());
            if (info.hasCustomSqlCondition()) {
                String condition = info.resolveCustomSqlCondition();
                if (condition != null && !condition.isEmpty()) {
                    if (customSqlConditions.length() > 0) {
                        customSqlConditions.append(" OR ");
                    }
                    customSqlConditions.append("(").append(condition).append(")");
                }
            }
        }
        String mergedCustomCondition = customSqlConditions.length() > 0
                ? customSqlConditions.toString() : null;
        return new DataScopeInfo(maxScope, tenantId, userId, Collections.unmodifiableSet(companies),
                Collections.unmodifiableSet(depts), Collections.unmodifiableSet(projects),
                Collections.unmodifiableSet(regions), mergedCustomCondition);
    }

    private DataScopeType parseScope(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        try {
            return DataScopeType.codeOf(code.trim());
        } catch (Exception e) {
            log.warn("未知 scope 类型：{}", code);
            return null;
        }
    }

    private Set<String> toStringSet(JsonNode arr) {
        if (arr == null || arr.isNull() || !arr.isArray()) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>();
        Iterator<JsonNode> items = arr.elements();
        while (items.hasNext()) {
            JsonNode item = items.next();
            String v = item.asText(null);
            if (StringUtils.isNotBlank(v)) {
                set.add(v.trim());
            }
        }
        return set;
    }

    private String trimToNull(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private Set<String> parseUserRoles(Map<String, Object> userInfo) {
        Object v = userInfo.get(properties.getRoleCodeField());
        if (v == null) {
            return Collections.emptySet();
        }
        return Arrays.stream(String.valueOf(v).split(","))
                .map(String::trim).filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    /**
     * 使指定角色的数据权限缓存失效。
     *
     * @param roleCode 角色编码
     */
    public void invalidate(String roleCode) {
        if (StringUtils.isNotBlank(roleCode)) {
            cache.invalidate(roleCode.trim());
        }
    }

    /**
     * 使所有数据权限缓存失效。
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 获取数据权限本地缓存实例。
     *
     * @return 本地缓存实例
     */
    public Cache<String, DataScopeInfo> getCache() {
        return cache;
    }
}
