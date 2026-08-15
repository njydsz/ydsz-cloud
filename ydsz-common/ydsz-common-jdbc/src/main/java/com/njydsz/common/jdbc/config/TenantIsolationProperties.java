package com.njydsz.common.jdbc.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 租户隔离配置属性（纯数据载体）
 *
 * <p>提供租户隔离相关的配置字段，业务逻辑由 common-tenant 模块的拦截器处理。
 *
 * <p>配置示例：
 * <pre>{@code
 * ydsz:
 *   jdbc:
 *     tenant-isolation:
 *       enabled: true
 *       mode: SINGLE
 *       tenant-fields:
 *         - column: tenant_id
 *           source: TENANT
 *       ignore-tables: [sys_config, sys_dict]
 * }</pre>
 *
 * <p><b>注意：</b>自 v1.9.0 起，此类仅作为配置数据载体，
 * 计算逻辑（如 {@code getActiveTenantFields}）已迁移至 common-tenant 模块。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 v2.0.0 起，租户配置统一收口至 {@code ydsz.tenant.*} 前缀，
 *             请使用 {@link com.njydsz.common.tenant.config.TenantProperties} 替代。
 *             旧配置前缀 {@code ydsz.jdbc.tenant-isolation.*} 将在 v3.0.0 移除。
 *             迁移示例：
 *             {@code ydsz.jdbc.tenant-isolation.enabled} → {@code ydsz.tenant.enabled}
 *             ；{@code ydsz.jdbc.tenant-isolation.mode} → {@code ydsz.tenant.mode}
 */
@Data
@ConfigurationProperties(prefix = "ydsz.jdbc.tenant-isolation")
@Deprecated
public class TenantIsolationProperties {

    /**
     * 是否启用租户隔离（默认 true）
     */
    private boolean enabled = true;

    /**
     * 忽略租户隔离的表列表（忽略大小写）
     */
    private Set<String> ignoreTables = new HashSet<>();

    /**
     * 租户字段名（默认 tenant_id），向后兼容字段
     */
    private String tenantColumn = "tenant_id";

    /**
     * 租户隔离模式（默认 SINGLE）
     */
    private TenantMode mode = TenantMode.SINGLE;

    /**
     * 租户字段配置列表
     */
    private List<TenantField> tenantFields = new ArrayList<>();

    /**
     * URL 级白名单（跳过租户隔离的请求路径）
     */
    private Set<String> anonUrls = new HashSet<>();

    /**
     * 租户隔离模式枚举
     */
    public enum TenantMode {
        /** 单租户模式 */
        SINGLE,
        /** 多级租户模式 */
        MULTI,
        /** 数据库隔离模式 */
        ISOLATE_DB
    }

    /**
     * 租户字段值来源标识
     */
    public enum TenantSource {
        /** 租户 ID */
        TENANT,
        /** 集团租户 ID */
        GROUP,
        /** 公司租户 ID */
        COMPANY,
        /** 用户 ID */
        USER
    }

    /**
     * 租户字段配置
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
