package com.remisoft.common.jdbc.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * 租户隔离配置属性。
 *
 * <p>控制租户级别数据隔离的开关行为，支持单租户模式和多级租户模式。
 *
 * <h3>模式说明</h3>
 * <ul>
 *   <li>{@link TenantMode#SINGLE}：只取 {@code tenant-fields} 的<b>第一个</b>字段注入 SQL</li>
 *   <li>{@link TenantMode#MULTI}：取 {@code tenant-fields} 的<b>全部</b>字段注入 SQL（AND 连接）</li>
 * </ul>
 *
 * <p>{@code tenant-fields} 一次性配好所有字段，切换模式只需改 {@code mode} 值。
 *
 * <h3>配置示例 — 单租户（默认，向后兼容）</h3>
 * <pre>
 * remi:
 *   jdbc:
 *     tenant-isolation:
 *       enabled: true
 *       mode: SINGLE
 *       tenant-fields:
 *         - column: tenant_id
 *           source: TENANT
 *       ignore-tables: [sys_config, sys_dict]
 *       anon-urls: [/auth/login, /auth/register]
 * </pre>
 *
 * <h3>配置示例 — 多级租户（集团+公司）</h3>
 * <pre>
 * remi:
 *   jdbc:
 *     tenant-isolation:
 *       enabled: true
 *       mode: MULTI
 *       tenant-fields:
 *         - column: group_tenant_id
 *           source: GROUP
 *         - column: company_tenant_id
 *           source: COMPANY
 *       ignore-tables: [sys_config, sys_dict]
 *       anon-urls: [/auth/login]
 * </pre>
 *
 * <h3>向后兼容</h3>
 * <p>当 {@code tenant-fields} 为空时，回退到 {@code tenant-column}（默认 {@code tenant_id}）+ SINGLE 模式，
 * 行为与旧版本完全一致。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@Configuration
@ConditionalOnProperty(prefix = "remi.jdbc", name = "enabled", matchIfMissing = true)
@ConfigurationProperties(prefix = "remi.jdbc.tenant-isolation")
public class TenantIsolationProperties {

    /**
     * 是否启用租户隔离（默认 true）。
     */
    private boolean enabled = true;

    /**
     * 忽略租户隔离的表列表（忽略大小写）。
     * 例如系统配置表、字典表等不需要租户隔离的表。
     */
    private Set<String> ignoreTables = new HashSet<>();

    /**
     * 租户字段名（默认 tenant_id）。
     *
     * <p>向后兼容字段：当 {@link #tenantFields} 为空时使用此字段。
     * 配置了 {@link #tenantFields} 后此字段被忽略。
     */
    private String tenantColumn = "tenant_id";

    /**
     * 租户隔离模式（默认 SINGLE）。
     *
     * <ul>
     *   <li>SINGLE：只取第一个字段</li>
     *   <li>MULTI：取全部字段</li>
     * </ul>
     */
    private TenantMode mode = TenantMode.SINGLE;

    /**
     * 租户字段配置列表（一次性配好，切换模式无需修改）。
     *
     * <p>为空时回退到 {@link #tenantColumn} + SINGLE 模式。
     */
    private List<TenantField> tenantFields = new ArrayList<>();

    /**
     * URL 级白名单（跳过租户隔离的请求路径）。
     *
     * <p>Web 层拦截器匹配到这些 URL 时，设置 RequestContext 跳过标记，
     * SQL 拦截器检测到标记后不注入租户条件。适用于登录、注册、验证码等公开接口。
     */
    private Set<String> anonUrls = new HashSet<>();

    /**
     * 获取生效的租户字段列表。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code tenantFields} 为空 → 回退到 {@code tenantColumn} + TENANT source</li>
     *   <li>SINGLE 模式 → 只取第一个</li>
     *   <li>MULTI 模式 → 取全部</li>
     * </ul>
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
     *
     * <p>决定 SQL 拦截器从哪里获取该字段的值：
     * <ul>
     *   <li>TENANT：优先 AuthInfoUtils.getTenantId()，回退 RequestContext.getTenantId()</li>
     *   <li>GROUP：RequestContext.get("groupTenantId")</li>
     *   <li>COMPANY：RequestContext.get("companyTenantId")</li>
     *   <li>USER：AuthInfoUtils.getUniqueId()</li>
     * </ul>
     */
    public enum TenantSource {
        /** 租户 ID（AuthInfo / RequestContext） */
        TENANT,
        /** 集团租户 ID（RequestContext） */
        GROUP,
        /** 公司租户 ID（RequestContext） */
        COMPANY,
        /** 用户 ID（AuthInfo） */
        USER
    }

    /**
     * 租户字段配置。
     */
    @Data
    public static class TenantField {
        /**
         * 数据库列名。
         */
        private String column;

        /**
         * 值来源标识（默认 TENANT）。
         */
        private TenantSource source = TenantSource.TENANT;

        public TenantField() {
        }

        public TenantField(String column, TenantSource source) {
            this.column = column;
            this.source = source;
        }
    }
}
