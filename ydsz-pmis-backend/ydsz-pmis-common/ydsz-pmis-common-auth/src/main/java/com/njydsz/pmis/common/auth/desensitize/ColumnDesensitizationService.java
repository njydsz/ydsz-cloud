package com.njydsz.pmis.common.auth.desensitize;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.auth.config.AuthProperties;
import com.njydsz.pmis.common.auth.model.UserInfo;
import com.njydsz.pmis.common.auth.service.RbacUserInfoService;
import com.njydsz.pmis.common.cache.YdszCache;
import com.njydsz.pmis.common.cache.api.Cache;
import com.njydsz.pmis.common.cache.builder.CacheType;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.common.redis.service.RedisService;
import com.njydsz.pmis.common.safe.desensitize.ColumnDesensitizationContext;
import com.njydsz.pmis.common.safe.desensitize.ColumnDesensitizationRule;
import com.njydsz.pmis.common.util.string.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 字段脱敏服务。
 *
 * <p>职责：
 * <ul>
 *   <li>从 Redis role-col-key 解析脱敏规则配置</li>
 *   <li>按角色加载并合并脱敏规则</li>
 *   <li>提供脱敏上下文查询</li>
 * </ul>
 *
 * <p><b>Redis 配置格式：</b>
 * <pre>{@code
 * {
 *   "visibleColumns": {
 *     "sys_user": [
 *       {"column": "phone", "rule": "PHONE"},
 *       {"column": "email", "rule": "EMAIL", "pattern": "xxx", "replacement": "xxx"}
 *     ]
 *   }
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see ColumnDesensitizationRule
 * @see ColumnDesensitizationContext
 */
@Slf4j
@Service
public class ColumnDesensitizationService {

    private final RedisService redisService;
    private final AuthProperties properties;
    private final RbacUserInfoService userInfoService;
    private final Cache<String, ColumnDesensitizationContext> cache;

    public ColumnDesensitizationService(RedisService redisService,
                                        AuthProperties properties,
                                        RbacUserInfoService userInfoService) {
        this.redisService = redisService;
        this.properties = properties;
        this.userInfoService = userInfoService;
        this.cache = YdszCache.<String, ColumnDesensitizationContext>newBuilder()
                .type(CacheType.TTL)
                .maximumSize(properties.getDesensitizeCacheMaxSize())
                .expireAfterWrite(properties.getDesensitizeCacheTtlSeconds(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * 根据角色编码加载脱敏上下文。
     *
     * @param roleCode 角色编码
     * @return 脱敏上下文
     */
    public ColumnDesensitizationContext loadByRoleCode(String roleCode) {
        if (StringUtils.isBlank(roleCode)) {
            return ColumnDesensitizationContext.empty();
        }

        ColumnDesensitizationContext cached = cache.getIfPresent(roleCode);
        if (cached != null) {
            return cached;
        }

        ColumnDesensitizationContext context = new ColumnDesensitizationContext();
        String json = redisService.get(String.format(properties.getRoleColKey(), roleCode.trim()), String.class);

        if (StringUtils.isNotBlank(json)) {
            parseAndMergeRules(json, context);
        }

        cache.put(roleCode, context);
        return context;
    }

    /**
     * 根据多个角色编码加载脱敏上下文（合并规则）。
     *
     * @param roleCodes 角色编码集合
     * @return 合并后的脱敏上下文
     */
    public ColumnDesensitizationContext loadByRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return ColumnDesensitizationContext.empty();
        }

        ColumnDesensitizationContext merged = new ColumnDesensitizationContext();
        for (String roleCode : roleCodes) {
            ColumnDesensitizationContext context = loadByRoleCode(roleCode);
            mergeContext(merged, context);
        }
        return merged;
    }

    /**
     * 根据当前访问令牌加载脱敏上下文。
     *
     * @param userId 当前用户ID
     * @param accessToken 访问令牌
     * @return 脱敏上下文
     */
    public ColumnDesensitizationContext loadByToken(String userId, String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return ColumnDesensitizationContext.empty();
        }
        UserInfo userInfo = userInfoService.loadUserInfo(accessToken);
        if (userInfo == null || !userInfo.isValid()) {
            return ColumnDesensitizationContext.empty();
        }
        String roleCode = userInfo.getRoleCode();
        if (StringUtils.isBlank(roleCode)) {
            return ColumnDesensitizationContext.empty();
        }
        Set<String> roleCodes = new HashSet<>();
        for (String code : roleCode.split(",")) {
            String trimmed = code.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                roleCodes.add(trimmed);
            }
        }
        return loadByRoleCodes(roleCodes);
    }

    /**
     * 清除指定角色的缓存。
     *
     * @param roleCode 角色编码
     */
    public void invalidate(String roleCode) {
        if (StringUtils.isNotBlank(roleCode)) {
            cache.invalidate(roleCode.trim());
        }
    }

    /**
     * 清除所有缓存。
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    private void parseAndMergeRules(String json, ColumnDesensitizationContext context) {
        try {
            // Use JsonUtils static methods (YdszJson engine)
            JsonNode root = YdszJson.readTree(json);
            if (root == null || root.isMissing()) {
                return;
            }

            JsonNode visibleColumns = root.get("visibleColumns");
            if (visibleColumns != null && !visibleColumns.isMissing()) {
                parseTableColumns(visibleColumns, context);
            }

            JsonNode editableColumns = root.get("editableColumns");
            if (editableColumns != null && !editableColumns.isMissing()) {
                parseTableColumns(editableColumns, context);
            }

            JsonNode desensitizeRules = root.get("desensitizeRules");
            if (desensitizeRules != null && !desensitizeRules.isMissing()) {
                parseTableColumns(desensitizeRules, context);
            }

        } catch (Exception e) {
            log.warn("解析脱敏规则失败：{}, error={}", json, e.getMessage());
        }
    }

    private void parseTableColumns(JsonNode tableColumns, ColumnDesensitizationContext context) {
        for (Map.Entry<String, JsonNode> entry : tableColumns.asMap().entrySet()) {
            String tableName = entry.getKey();
            JsonNode columnValue = entry.getValue();
            if (columnValue == null || columnValue.isNull()) {
                return;
            }

            if (columnValue.isArray()) {
                parseColumnArray(tableName, columnValue, context);
            } else if (columnValue.isTextual()) {
                parseColumnString(tableName, columnValue.asText(), context);
            }
        }
    }

    private void parseColumnArray(String tableName, JsonNode columns, ColumnDesensitizationContext context) {
        for (JsonNode item : columns) {
            if (item == null || item.isNull()) {
                continue;
            }

            if (item.isObject()) {
                String column = item.has("column") ? item.get("column").asText("") : "";
                String ruleCode = item.has("rule") ? item.get("rule").asText("") : "";
                String customPattern = item.has("pattern") ? item.get("pattern").asText("") : "";
                String customReplacement = item.has("replacement") ? item.get("replacement").asText("") : "";

                if (StringUtils.isNotBlank(column) && StringUtils.isNotBlank(ruleCode)) {
                    ColumnDesensitizationRule rule = ColumnDesensitizationRule.codeOf(ruleCode);
                    if (rule == ColumnDesensitizationRule.CUSTOM) {
                        context.addRule(tableName, column, rule, customPattern, customReplacement);
                    } else {
                        context.addRule(tableName, column, rule);
                    }
                }
            } else if (item.isTextual()) {
                String column = item.asText();
                context.addRule(tableName, column, (ColumnDesensitizationRule) null);
            }
        }
    }

    private void parseColumnString(String tableName, String columns, ColumnDesensitizationContext context) {
        String[] columnArray = columns.split(",");
        for (String column : columnArray) {
            column = column.trim();
            if (StringUtils.isNotBlank(column)) {
                context.addRule(tableName, column, (ColumnDesensitizationRule) null);
            }
        }
    }

    private void mergeContext(ColumnDesensitizationContext target, ColumnDesensitizationContext source) {
        if (source == null || source.isEmpty()) {
            return;
        }

        for (String table : source.getAllTables()) {
            Set<String> columns = source.getColumns(table);
            for (String column : columns) {
                ColumnDesensitizationContext.DesensitizationRuleConfig config = source.getRule(table, column);
                if (config != null) {
                    target.addRule(table, column, config);
                }
            }
        }
    }

}