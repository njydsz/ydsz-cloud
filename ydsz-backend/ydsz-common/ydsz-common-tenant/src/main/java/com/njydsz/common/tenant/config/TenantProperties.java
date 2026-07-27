package com.njydsz.common.tenant.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 多租户配置属性。
 *
 * <p>配置前缀：{@code ydsz.tenant}
 *
 * <h3>模式说明</h3>
 * <ul>
 *   <li>{@link TenantMode#SINGLE}：只取 {@code tenant-fields} 的第一个字段注入 SQL</li>
 *   <li>{@link TenantMode#MULTI}：取 {@code tenant-fields} 的全部字段注入 SQL（AND 连接）</li>
 *   <li>{@link TenantMode#ISOLATE_DB}：独立数据源模式，每租户使用独立数据库</li>
 * </ul>
 *
 * <h3>配置示例 — 单租户</h3>
 * <pre>
 * ydsz:
 *   tenant:
 *     enabled: true
 *     mode: SINGLE
 *     tenant-column: tenant_id
 *     ignore-tables: [ydsz_tenant, ydsz_tenant_plan]
 *     anon-urls: [/auth/login, /auth/register]
 * </pre>
 *
 * <h3>配置示例 — 多级租户（集团+公司）</h3>
 * <pre>
 * ydsz:
 *   tenant:
 *     enabled: true
 *     mode: MULTI
 *     tenant-fields:
 *       - column: group_tenant_id
 *         source: GROUP
 *       - column: company_tenant_id
 *         source: COMPANY
 * </pre>
 *
 * <h3>配置示例 — per-table 列名覆盖</h3>
 * <pre>
 * ydsz:
 *   tenant:
 *     table-column-mapping:
 *       ydsz_file_node: org_id
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.tenant")
public class TenantProperties {

    /**
     * 是否启用多租户（默认 false，不启用）。
     *
     * <p>子应用不引入 common-tenant 依赖或设为 false 时，无任何租户逻辑。
     */
    private boolean enabled = false;

    /**
     * 租户隔离模式（默认 SINGLE）。
     */
    @NotNull
    private TenantMode mode = TenantMode.SINGLE;

    /**
     * 默认租户列名（默认 tenant_id）。
     *
     * <p>当 {@link #tenantFields} 为空时使用此字段。
     * 配置了 {@link #tenantFields} 后此字段被忽略。
     */
    @NotBlank
    private String tenantColumn = "tenant_id";

    /**
     * 超级管理员租户 ID（默认 "0"）。
     *
     * <p>此租户 ID 的用户可跨租户操作，SQL 拦截器不注入租户条件。
     */
    @NotBlank
    private String superTenantId = "0";

    /**
     * 系统租户 ID（默认 "0"）。
     *
     * <p>定时任务、MQ Consumer、@Async 等无用户上下文的场景使用此租户 ID。
     */
    @NotBlank
    private String systemTenantId = "0";

    /**
     * 租户字段配置列表（一次性配好，切换模式无需修改）。
     *
     * <p>为空时回退到 {@link #tenantColumn} + SINGLE 模式。
     */
    private List<TenantField> tenantFields = new ArrayList<>();

    /**
     * per-table 列名覆盖映射。
     *
     * <p>key=表名（小写），value=列名。
     * 配置了此映射的表使用自定义列名，而非全局默认 {@link #tenantColumn}。
     */
    private Map<String, String> tableColumnMapping = new HashMap<>();

    /**
     * 忽略租户隔离的表列表（忽略大小写）。
     */
    private Set<String> ignoreTables = new HashSet<>();

    /**
     * URL 级白名单（跳过租户隔离的请求路径）。
     */
    private Set<String> anonUrls = new HashSet<>();

    /**
     * 获取生效的租户字段列表。
     *
     * @return 生效的租户字段列表（不可变）
     */
    public List<TenantField> getActiveTenantFields() {
        if (tenantFields == null || tenantFields.isEmpty()) {
            return List.of(new TenantField(tenantColumn, TenantSource.TENANT));
        }
        if (mode == TenantMode.SINGLE) {
            return List.of(tenantFields.get(0));
        }
        return Collections.unmodifiableList(tenantFields);
    }

    /**
     * 解析表对应的租户列名。
     *
     * <p>优先级：per-table 映射 > 全局默认
     *
     * @param tableName 表名
     * @return 列名
     */
    public String resolveColumn(String tableName) {
        if (tableName == null || tableColumnMapping == null || tableColumnMapping.isEmpty()) {
            return tenantColumn;
        }
        String mapped = tableColumnMapping.get(tableName.toLowerCase());
        return mapped != null ? mapped : tenantColumn;
    }

    /**
     * 获取规范化后的忽略表集合（小写化）。
     *
     * @return 小写化的忽略表集合
     */
    public Set<String> getNormalizedIgnoreTables() {
        if (ignoreTables == null || ignoreTables.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>(ignoreTables.size());
        for (String table : ignoreTables) {
            if (table != null) {
                normalized.add(table.trim().toLowerCase());
            }
        }
        return normalized;
    }

    /**
     * 获取规范化后的白名单 URL 集合（去空白）。
     *
     * @return 规范化的白名单 URL 集合
     */
    public Set<String> getNormalizedAnonUrls() {
        if (anonUrls == null || anonUrls.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>(anonUrls.size());
        for (String url : anonUrls) {
            if (url != null) {
                normalized.add(url.trim());
            }
        }
        return normalized;
    }

    /**
     * 租户隔离模式。
     */
    public enum TenantMode {
        /** 单租户模式：只取第一个字段 */
        SINGLE,
        /** 多级租户模式：取全部字段 */
        MULTI,
        /** 数据库隔离模式：每个租户使用独立数据源 */
        ISOLATE_DB
    }

    /**
     * 租户字段值来源标识。
     */
    public enum TenantSource {
        /** 租户 ID（TenantContextHolder） */
        TENANT,
        /** 集团租户 ID */
        GROUP,
        /** 公司租户 ID */
        COMPANY,
        /** 用户 ID */
        USER
    }

    /**
     * 租户字段配置。
     */
    @Data
    public static class TenantField {
        /** 数据库列名 */
        private String column;
        /** 值来源标识（默认 TENANT） */
        private TenantSource source = TenantSource.TENANT;

        public TenantField() {
        }

        public TenantField(String column, TenantSource source) {
            this.column = column;
            this.source = source;
        }
    }
}
