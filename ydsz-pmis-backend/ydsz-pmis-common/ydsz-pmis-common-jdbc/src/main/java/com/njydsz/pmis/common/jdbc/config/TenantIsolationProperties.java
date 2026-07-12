package com.njydsz.pmis.common.jdbc.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 租户隔离配置属性。
 *
 * <p>控制租户级别数据隔离的开关行为，包括是否启用、
 * 忽略的表列表以及租户字段名等配置。
 *
 * <p>配置示例：
 * <pre>
 * remi:
 *   jdbc:
 *     tenant-isolation:
 *       enabled: true
 *       tenant-column: tenant_id
 *       ignore-tables:
 *         - sys_config
 *         - sys_dict
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
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
     */
    private String tenantColumn = "tenant_id";

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
}
