package com.remisoft.common.tenant.datasource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import com.remisoft.common.jdbc.datasource.DynamicDataSourceContextHolder;
import com.remisoft.common.jdbc.datasource.DynamicRoutingDataSource;
import com.remisoft.common.tenant.config.TenantProperties;
import com.remisoft.common.tenant.metrics.TenantMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * 租户数据源路由器。
 *
 * <p>在 ISOLATE_DB 模式下，根据当前租户 ID 动态切换到对应的数据源。
 *
 * <p><b>路由缓存：</b>租户 ID → 数据源 Key 的映射缓存在 {@link ConcurrentHashMap} 中，
 * 避免每次请求都遍历 {@link DynamicRoutingDataSource#getDataSources()}。
 * 缓存生命周期与 Bean 一致（应用启动时预热，运行时只读）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class TenantDataSourceRouter {

    private final DynamicRoutingDataSource routingDataSource;
    private final TenantProperties properties;
    private final TenantMetrics metrics;

    /** 租户 ID → 数据源 Key 缓存（预热后只读） */
    private final Map<String, String> datasourceKeyCache = new ConcurrentHashMap<>();

    public TenantDataSourceRouter(DynamicRoutingDataSource routingDataSource,
                                   TenantProperties properties) {
        this(routingDataSource, properties, null);
    }

    public TenantDataSourceRouter(DynamicRoutingDataSource routingDataSource,
                                   TenantProperties properties,
                                   TenantMetrics metrics) {
        this.routingDataSource = routingDataSource;
        this.properties = properties;
        this.metrics = metrics;
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

        // 从缓存查找数据源 Key
        String datasourceKey = datasourceKeyCache.computeIfAbsent(tenantId, this::resolveDatasourceKey);

        if (datasourceKey == null) {
            log.warn("租户 {} 未配置数据源，使用默认数据源", tenantId);
            return;
        }

        // 验证数据源是否存在
        Map<Object, DataSource> dataSources = routingDataSource.getDataSources();
        if (!dataSources.containsKey(datasourceKey)) {
            log.warn("租户 {} 的数据源 {} 不存在，使用默认数据源", tenantId, datasourceKey);
            // 移除失效缓存
            datasourceKeyCache.remove(tenantId);
            return;
        }

        DynamicDataSourceContextHolder.push(datasourceKey);
        if (metrics != null) metrics.recordDatasourceSwitch();
        log.debug("租户 {} 切换到数据源 {}", tenantId, datasourceKey);
    }

    /**
     * 解析租户对应的数据源 Key。
     *
     * <p>当前实现使用 {@code "tenant_" + tenantId} 约定。
     * <p>生产环境应从 {@code remi_tenant} 表查询 {@code datasource_key} 字段。
     *
     * @param tenantId 租户 ID
     * @return 数据源 Key，不存在返回 null
     */
    private String resolveDatasourceKey(String tenantId) {
        // TODO: 生产环境从 remi_tenant 表查询 datasource_key 字段
        // 此处使用约定命名：tenant_{tenantId}
        String key = "tenant_" + tenantId;
        Map<Object, DataSource> dataSources = routingDataSource.getDataSources();
        if (dataSources.containsKey(key)) {
            return key;
        }
        return null;
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
