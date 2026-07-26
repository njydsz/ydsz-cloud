package com.njydsz.common.jdbc.interceptor;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.jdbc.config.TenantIsolationProperties;
import com.njydsz.common.jdbc.datasource.DynamicDataSourceContextHolder;
import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 租户数据源路由器
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
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>必须在请求开始前调用 {@link #routeToTenantDataSource(String)} 切换数据源</li>
 *   <li>请求结束后必须调用 {@link #restoreDataSource()} 恢复默认数据源</li>
 *   <li>如果租户未配置 datasource_key，则使用默认数据源</li>
 *   <li>超级管理员（tenantId = "0" 或 null）始终使用默认数据源</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantDataSourceRouter {

    private final DynamicRoutingDataSource routingDataSource;
    private final TenantIsolationProperties properties;

    public TenantDataSourceRouter(DynamicRoutingDataSource routingDataSource,
                                   TenantIsolationProperties properties) {
        this.routingDataSource = routingDataSource;
        this.properties = properties;
    }

    /**
     * 根据租户 ID 路由到对应数据源
     *
     * <p>从 RequestContext 获取租户 ID，查询 ydsz_tenant 表的 datasource_key，
     * 然后切换到对应数据源。如果租户未配置 datasource_key 或为超级管理员，
     * 则使用默认数据源。
     *
     * @param tenantId 租户 ID（可为 null，表示使用默认数据源）
     */
    public void routeToTenantDataSource(String tenantId) {
        // 检查是否启用 ISOLATE_DB 模式
        if (properties.getMode() != TenantIsolationProperties.TenantMode.ISOLATE_DB) {
            return;
        }

        // 超级管理员或未设置租户 ID，使用默认数据源
        if (tenantId == null || "0".equals(tenantId)) {
            log.debug("超级管理员或未设置租户 ID，使用默认数据源");
            return;
        }

        // 从 RequestContext 获取 datasource_key（由 WebAuthFilter 或业务层设置）
        String datasourceKey = (String) RequestContext.get("tenant_datasource_key");
        if (datasourceKey == null || datasourceKey.isEmpty()) {
            log.debug("租户 {} 未配置 datasource_key，使用默认数据源", tenantId);
            return;
        }

        // 检查数据源是否存在
        Map<Object, DataSource> dataSources = routingDataSource.getDataSources();
        if (!dataSources.containsKey(datasourceKey)) {
            log.warn("租户 {} 的数据源 {} 不存在，使用默认数据源", tenantId, datasourceKey);
            return;
        }

        // 切换数据源
        DynamicDataSourceContextHolder.push(datasourceKey);
        log.debug("租户 {} 切换到数据源 {}", tenantId, datasourceKey);
    }

    /**
     * 恢复默认数据源
     *
     * <p>在请求结束后调用，恢复线程上下文到默认数据源，
     * 防止线程复用导致数据源串扰。
     */
    public void restoreDataSource() {
        if (properties.getMode() != TenantIsolationProperties.TenantMode.ISOLATE_DB) {
            return;
        }
        DynamicDataSourceContextHolder.poll();
        log.debug("恢复默认数据源");
    }

    /**
     * 检查当前是否处于 ISOLATE_DB 模式
     *
     * @return true-ISOLATE_DB 模式，false-其他模式
     */
    public boolean isIsolateDbMode() {
        return properties.getMode() == TenantIsolationProperties.TenantMode.ISOLATE_DB;
    }
}
