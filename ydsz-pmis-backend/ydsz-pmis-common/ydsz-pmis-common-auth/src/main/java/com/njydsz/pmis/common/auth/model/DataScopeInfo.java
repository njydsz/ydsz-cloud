package com.njydsz.pmis.common.auth.model;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.njydsz.pmis.common.core.enums.DataScopeType;

import lombok.Getter;

/**
 * 数据权限信息载体。
 *
 * <p>用于表达"当前用户可访问的数据范围"：
 * <ul>
 *   <li>组织维度：scope + companyIds + deptIds（集团/公司/部门）</li>
 *   <li>项目维度：projectIds</li>
 *   <li>区域维度：regionIds</li>
 *   <li>自定义维度：customSqlCondition（自定义 SQL 条件）</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>scope：权限范围（tenant/group/company/dept/user/project/region/custom）</li>
 *   <li>companyIds：可访问公司 ID 集合</li>
 *   <li>deptIds：可访问部门 ID 集合</li>
 *   <li>projectIds：可访问项目 ID 集合</li>
 *   <li>regionIds：可访问区域 ID 集合</li>
 *   <li>customSqlCondition：自定义 SQL 条件片段</li>
 * </ul>
 *
 * @since 1.0.0
 * 
 * @see DataScopeType
 */
@Getter
public class DataScopeInfo {

    private final DataScopeType scope;
    private final String tenantId;
    private final String userId;
    private final Set<String> companyIds;
    private final Set<String> deptIds;
    private final Set<String> projectIds;
    private final Set<String> regionIds;
    private final String customSqlCondition;
    private final String customSqlConditionTemplate;

    public DataScopeInfo(DataScopeType scope, Set<String> companyIds, Set<String> deptIds) {
        this(scope, null, null, companyIds, deptIds, Collections.emptySet(), Collections.emptySet(), null, null);
    }

    public DataScopeInfo(DataScopeType scope, Set<String> companyIds, Set<String> deptIds,
                         Set<String> projectIds, Set<String> regionIds) {
        this(scope, null, null, companyIds, deptIds, projectIds, regionIds, null, null);
    }

    public DataScopeInfo(DataScopeType scope, String tenantId, String userId,
                         Set<String> companyIds, Set<String> deptIds,
                         Set<String> projectIds, Set<String> regionIds) {
        this(scope, tenantId, userId, companyIds, deptIds, projectIds, regionIds, null, null);
    }

    public DataScopeInfo(DataScopeType scope, String tenantId, String userId,
                         Set<String> companyIds, Set<String> deptIds,
                         Set<String> projectIds, Set<String> regionIds,
                         String customSqlCondition) {
        this(scope, tenantId, userId, companyIds, deptIds, projectIds, regionIds, customSqlCondition, null);
    }

    public DataScopeInfo(DataScopeType scope, String tenantId, String userId,
                         Set<String> companyIds, Set<String> deptIds,
                         Set<String> projectIds, Set<String> regionIds,
                         String customSqlCondition, String customSqlConditionTemplate) {
        this.scope = scope;
        this.tenantId = tenantId;
        this.userId = userId;
        this.companyIds = companyIds != null ? Collections.unmodifiableSet(companyIds) : Collections.emptySet();
        this.deptIds = deptIds != null ? Collections.unmodifiableSet(deptIds) : Collections.emptySet();
        this.projectIds = projectIds != null ? Collections.unmodifiableSet(projectIds) : Collections.emptySet();
        this.regionIds = regionIds != null ? Collections.unmodifiableSet(regionIds) : Collections.emptySet();
        this.customSqlCondition = customSqlCondition;
        this.customSqlConditionTemplate = customSqlConditionTemplate;
    }

    public static DataScopeInfo empty() {
        return new DataScopeInfo(null, null, null, Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), null, null);
    }

    public boolean isCustom() {
        return DataScopeType.CUSTOM.equals(scope);
    }

    public boolean hasCustomSqlCondition() {
        return customSqlCondition != null && !customSqlCondition.trim().isEmpty();
    }

    public String resolveCustomSqlCondition() {
        if (customSqlConditionTemplate != null && !customSqlConditionTemplate.trim().isEmpty()) {
            return resolveTemplate(customSqlConditionTemplate);
        }
        return customSqlCondition;
    }

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(;|--|/\\*|\\*/|\\b(DROP|DELETE|INSERT|UPDATE|TRUNCATE|ALTER|EXEC|EXECUTE|UNION|INTO|LOAD|OUTFILE)\\b)"
    );

    private static final Pattern SAFE_VALUE_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_\\-.,:@/]+$"
    );

    private String resolveTemplate(String template) {
        if (template == null || template.isEmpty()) {
            return null;
        }
        if (SQL_INJECTION_PATTERN.matcher(template).find()) {
            throw new SecurityException("SQL模板包含潜在注入风险: " + template);
        }
        String result = template;
        result = result.replace("{{userId}}", sanitizeSqlValue(userId));
        result = result.replace("{{tenantId}}", sanitizeSqlValue(tenantId));
        result = result.replace("{{companyIds}}", sanitizeSqlSet(companyIds));
        result = result.replace("{{deptIds}}", sanitizeSqlSet(deptIds));
        result = result.replace("{{projectIds}}", sanitizeSqlSet(projectIds));
        result = result.replace("{{regionIds}}", sanitizeSqlSet(regionIds));
        return result;
    }

    private String sanitizeSqlValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (!SAFE_VALUE_PATTERN.matcher(value).matches()) {
            return "";
        }
        String sanitized = value.replace("'", "''").replace("\\", "\\\\");
        if (SQL_INJECTION_PATTERN.matcher(sanitized).find()) {
            return "";
        }
        return sanitized;
    }

    private String sanitizeSqlSet(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return "";
        }
        return set.stream()
                .map(this::sanitizeSqlValue)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.joining(","));
    }

    public Builder toBuilder() {
        return new Builder()
                .scope(scope)
                .tenantId(tenantId)
                .userId(userId)
                .companyIds(companyIds)
                .deptIds(deptIds)
                .projectIds(projectIds)
                .regionIds(regionIds)
                .customSqlCondition(customSqlCondition)
                .customSqlConditionTemplate(customSqlConditionTemplate);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private DataScopeType scope;
        private String tenantId;
        private String userId;
        private Set<String> companyIds;
        private Set<String> deptIds;
        private Set<String> projectIds;
        private Set<String> regionIds;
        private String customSqlCondition;
        private String customSqlConditionTemplate;

        public Builder scope(DataScopeType scope) {
            this.scope = scope;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder companyIds(Set<String> companyIds) {
            this.companyIds = companyIds;
            return this;
        }

        public Builder deptIds(Set<String> deptIds) {
            this.deptIds = deptIds;
            return this;
        }

        public Builder projectIds(Set<String> projectIds) {
            this.projectIds = projectIds;
            return this;
        }

        public Builder regionIds(Set<String> regionIds) {
            this.regionIds = regionIds;
            return this;
        }

        public Builder customSqlCondition(String customSqlCondition) {
            this.customSqlCondition = customSqlCondition;
            return this;
        }

        public Builder customSqlConditionTemplate(String customSqlConditionTemplate) {
            this.customSqlConditionTemplate = customSqlConditionTemplate;
            return this;
        }

        public DataScopeInfo build() {
            return new DataScopeInfo(scope, tenantId, userId, companyIds, deptIds, projectIds, regionIds,
                    customSqlCondition, customSqlConditionTemplate);
        }
    }
}