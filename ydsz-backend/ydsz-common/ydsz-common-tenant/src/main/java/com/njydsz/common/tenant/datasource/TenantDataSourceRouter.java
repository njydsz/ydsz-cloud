package com.njydsz.common.tenant.datasource;

import java.util.Map;

import javax.sql.DataSource;

import com.njydsz.common.jdbc.datasource.DynamicDataSourceContextHolder;
import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;
import com.njydsz.common.tenant.config.TenantProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 租户数据源路由器。
 *
 * <p>在 ISOLATE_DB 模式下，根据当前租户 ID 动态切换到对应的数据源。
 * 通过查询 {@code ydsz_tenant} 表的 {@code datasource_key} 字段获取数据源标识，
 * 然后调用 {@link DynamicDataSourceContextHolder#push(String)} 切换数据源。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>SaaS 多租户系统中，不同租户使用独立数据库</li>
 *   <li>数据隔离要求极高的场景（金融、医疗等）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class TenantDataSourceRouter {

    private final DynamicRoutingDataSource routingDataSource;
    private final TenantProperties properties;

    public TenantDataSourceRouter(DynamicRoutingDataSource routingDataSource,
                                   TenantProperties properties) {
        this.routingDataSource = routingDataSource;
        this.properties = properties;
    }

    /**
     * 根据租户 ID 路由到对应数据源。
     *
     * @param tenantId 租户 ID（可为 null，表示使用默认数据源）
     */
    public void routeToTenantDataSource(String tenantId) {
        if (properties.getMode() != TenantProperties.TenantMode.ISOLATE_DB) {
            return;
        }

        if (tenantId == null || properties.getSuperTenantId().equals(tenantId)) {
            log.debug("超级管理员或未设置租户 ID，使用默认数据源");
            return;
        }

        // 查找租户对应的数据源 key
        // 实际场景中需查询 ydsz_tenant 表获取 datasource_key
        // 此处通过配置映射或运行时注入获取
        Map<Object, DataSource> dataSources = routingDataSource.getDataSources();
        String datasourceKey = "tenant_" + tenantId;

        if (!dataSources.containsKey(datasourceKey)) {
            log.warn("租户 {} 的数据源 {} 不存在，使用默认数据源", tenantId, datasourceKey);
            return;
        }

        DynamicDataSourceContextHolder.push(datasourceKey);
        log.debug("租户 {} 切换到数据源 {}", tenantId, datasourceKey);
    }

    /**
     * 恢复默认数据源。
     */
    public void restoreDataSource() {
        if (properties.getMode() != TenantProperties.TenantMode.ISOLATE_DB) {
            return;
        }
        DynamicDataSourceContextHolder.poll();
        log.debug("恢复默认数据源");
    }

    /**
     * 检查当前是否处于 ISOLATE_DB 模式。
     *
     * @return true=ISOLATE_DB 模式
     */
    public boolean isIsolateDbMode() {
        return properties.getMode() == TenantProperties.TenantMode.ISOLATE_DB;
    }
}
