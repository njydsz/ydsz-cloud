package com.njydsz.common.auth.model;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.njydsz.common.core.constant.DataScopeConstants;

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
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@Getter
public class DataScopeInfo {

    /** 数据权限范围类型编码（tenant/group/company/dept/user/project/region/custom）。 */
    private final String scope;
    /** 租户 ID；为 {@code null} 时沿用请求上下文中的租户。 */
    private final String tenantId;
    /** 用户 ID；用于按用户维度进一步收窄数据范围，可为 {@code null}。 */
    private final String userId;
    /** 可访问公司 ID 集合；构造时包装为不可变集合，永不为 {@code null}。 */
    private final Set<String> companyIds;
    /** 可访问部门 ID 集合；构造时包装为不可变集合，永不为 {@code null}。 */
    private final Set<String> deptIds;
    /** 可访问项目 ID 集合；构造时包装为不可变集合，永不为 {@code null}。 */
    private final Set<String> projectIds;
    /** 可访问区域 ID 集合；构造时包装为不可变集合，永不为 {@code null}。 */
    private final Set<String> regionIds;
    /** 直接给定的自定义 SQL 条件片段（已拼接），优先级高于模板生成结果。 */
    private final String customSqlCondition;
    /** 自定义 SQL 条件模板，支持 {@code {{userId}}} 等占位符，解析时注入并做防注入清洗。 */
    private final String customSqlConditionTemplate;

    public DataScopeInfo(String scope, Set<String> companyIds, Set<String> deptIds) {
        this(scope, null, null, companyIds, deptIds, Collections.emptySet(), Collections.emptySet(), null, null);
    }

    public DataScopeInfo(String scope, Set<String> companyIds, Set<String> deptIds,
                         Set<String> projectIds, Set<String> regionIds) {
        this(scope, null, null, companyIds, deptIds, projectIds, regionIds, null, null);
    }

    public DataScopeInfo(String scope, String tenantId, String userId,
                         Set<String> companyIds, Set<String> deptIds,
                         Set<String> projectIds, Set<String> regionIds) {
        this(scope, tenantId, userId, companyIds, deptIds, projectIds, regionIds, null, null);
    }

    public DataScopeInfo(String scope, String tenantId, String userId,
                         Set<String> companyIds, Set<String> deptIds,
                         Set<String> projectIds, Set<String> regionIds,
                         String customSqlCondition) {
        this(scope, tenantId, userId, companyIds, deptIds, projectIds, regionIds, customSqlCondition, null);
    }

    public DataScopeInfo(String scope, String tenantId, String userId,
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

    /**
     * 返回空数据权限实例。
     *
     * <p>所有维度集合为空、scope 为 {@code null}，表示无任何额外数据范围约束（等同于"全部可见"）。
     * 每次返回新建实例，调用方不可依赖其唯一性。</p>
     *
     * @return 空数据权限实例
     */
    public static DataScopeInfo empty() {
        return new DataScopeInfo(null, null, null, Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), null, null);
    }

    /**
     * 判断当前是否为自定义（CUSTOM）范围类型。
     *
     * @return 当 {@link #scope} 为 {@link DataScopeConstants#CUSTOM} 时返回 {@code true}
     */
    public boolean isCustom() {
        return DataScopeConstants.CUSTOM.equals(scope);
    }

    /**
     * 判断是否存在生效的自定义 SQL 条件。
     *
     * <p>模板（{@code customSqlConditionTemplate}）或直给片段（{@code customSqlCondition}）任一非空即视为存在。</p>
     *
     * @return 是否存在自定义 SQL 条件
     */
    public boolean hasCustomSqlCondition() {
        return customSqlCondition != null && !customSqlCondition.trim().isEmpty();
    }

    /**
     * 解析并返回最终生效的自定义 SQL 条件。
     *
     * <p>若配置了 {@code customSqlConditionTemplate} 则按模板注入当前用户/租户及各维度 ID 并做 SQL 注入清洗后返回；
     * 否则回退到直接给定的 {@code customSqlCondition}。模板含注入风险字符时抛出 {@link SecurityException} 阻断拼接。</p>
     *
     * @return 解析后的 SQL 条件片段；无模板且无直给条件时返回 {@code null}
     */
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

    /**
     * 基于当前实例创建构建器，便于在保留已有字段的前提下做局部修改（不可变对象的副本构造模式）。
     *
     * @return 预填充了当前所有字段值的 {@link Builder}
     */
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

    /**
     * 创建空白构建器。
     *
     * <p>若需在现有实例基础上修改，请改用 {@link #toBuilder()}。
     *
     * @return 全新的 {@link Builder}，各字段均为默认值
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link DataScopeInfo} 构建器。
     *
     * <p>所有 setter 均<b>不做校验</b>，集合类字段允许传 {@code null}，
     * 由 {@link #build()} 交给构造器统一包装为不可变空集合。
     */
    public static class Builder {
        private String scope;
        private String tenantId;
        private String userId;
        private Set<String> companyIds;
        private Set<String> deptIds;
        private Set<String> projectIds;
        private Set<String> regionIds;
        private String customSqlCondition;
        private String customSqlConditionTemplate;

        /**
         * 设置数据权限范围类型编码，决定后续按哪一维度收窄数据。
         *
         * @param scope 范围类型编码；为 {@code null} 时由消费方按最严策略处理
         * @return 当前 Builder，便于链式调用
         */
        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        /**
         * 设置租户 ID。
         *
         * @param tenantId 租户 ID；为 {@code null} 时沿用请求上下文中的租户
         * @return 当前 Builder，便于链式调用
         */
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * 设置用户 ID，用于按用户维度进一步收窄范围。
         *
         * <p>同时作为 {@code customSqlConditionTemplate} 中 <code>{{userId}}</code> 占位符的填充值。
         *
         * @param userId 用户 ID，可为 {@code null}
         * @return 当前 Builder，便于链式调用
         */
        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        /**
         * 设置可访问公司 ID 集合。
         *
         * @param companyIds 公司 ID 集合；传 {@code null} 等价于空集合（不放宽权限）
         * @return 当前 Builder，便于链式调用
         */
        public Builder companyIds(Set<String> companyIds) {
            this.companyIds = companyIds;
            return this;
        }

        /**
         * 设置可访问部门 ID 集合。
         *
         * @param deptIds 部门 ID 集合；传 {@code null} 等价于空集合（不放宽权限）
         * @return 当前 Builder，便于链式调用
         */
        public Builder deptIds(Set<String> deptIds) {
            this.deptIds = deptIds;
            return this;
        }

        /**
         * 设置可访问项目 ID 集合。
         *
         * @param projectIds 项目 ID 集合；传 {@code null} 等价于空集合（不放宽权限）
         * @return 当前 Builder，便于链式调用
         */
        public Builder projectIds(Set<String> projectIds) {
            this.projectIds = projectIds;
            return this;
        }

        /**
         * 设置可访问区域 ID 集合。
         *
         * @param regionIds 区域 ID 集合；传 {@code null} 等价于空集合（不放宽权限）
         * @return 当前 Builder，便于链式调用
         */
        public Builder regionIds(Set<String> regionIds) {
            this.regionIds = regionIds;
            return this;
        }

        /**
         * 设置已拼接好的自定义 SQL 条件片段。
         *
         * <p><b>安全提示</b>：本字段会直接参与 SQL 拼接，
         * 优先级高于 {@link #customSqlConditionTemplate(String)} 的解析结果。
         * 调用方必须自行确保内容可信，禁止直接透传前端入参。
         *
         * @param customSqlCondition SQL 条件片段，可为 {@code null}
         * @return 当前 Builder，便于链式调用
         */
        public Builder customSqlCondition(String customSqlCondition) {
            this.customSqlCondition = customSqlCondition;
            return this;
        }

        /**
         * 设置自定义 SQL 条件模板。
         *
         * <p>支持 <code>{{userId}}</code> 等占位符，解析时注入实际值并执行防注入清洗，
         * 因此比 {@link #customSqlCondition(String)} 更安全，应优先使用。
         *
         * @param customSqlConditionTemplate SQL 条件模板，可为 {@code null}
         * @return 当前 Builder，便于链式调用
         */
        public Builder customSqlConditionTemplate(String customSqlConditionTemplate) {
            this.customSqlConditionTemplate = customSqlConditionTemplate;
            return this;
        }

        /**
         * 构建不可变的 {@link DataScopeInfo} 实例。
         *
         * <p>各集合字段会在构造器中包装为不可变集合，{@code null} 统一归一化为空集合，
         * 因此产物的集合类 getter 永不返回 {@code null}。
         *
         * @return 构建完成的数据权限信息
         */
        public DataScopeInfo build() {
            return new DataScopeInfo(scope, tenantId, userId, companyIds, deptIds, projectIds, regionIds,
                    customSqlCondition, customSqlConditionTemplate);
        }
    }
}
