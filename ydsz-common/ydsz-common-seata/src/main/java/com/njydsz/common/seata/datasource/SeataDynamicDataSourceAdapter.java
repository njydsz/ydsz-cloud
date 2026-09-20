package com.njydsz.common.seata.datasource;

import com.njydsz.common.jdbc.datasource.DynamicRoutingDataSource;
import io.seata.rm.datasource.DataSourceProxy;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Seata 兼容的动态路由数据源适配器。
 *
 * <p>解决 Seata {@link DataSourceProxy} 与 YDSZ 自研 {@link DynamicRoutingDataSource} 的集成冲突：
 * <ul>
 *   <li>Seata AT 模式需要包装物理 DataSource 为 {@link DataSourceProxy}</li>
 *   <li>YDSZ 通过 {@link DynamicRoutingDataSource} 支持运行时动态切换</li>
 *   <li>两者集成不当会导致路由失效或 XID 跨库断裂</li>
 * </ul>
 *
 * <p><b>设计要点：</b>重写 {@link #addDataSource(Object, DataSource)} 方法，在数据源注册时
 * 自动将物理 {@link DataSource} 包装为 Seata {@link DataSourceProxy}，
 * 使 Seata AT 感知每个物理数据源，同时保留 YDSZ @DS 动态路由能力。
 *
 * <p><b>装配顺序：</b>
 * <pre>
 *   物理 DataSource (HikariCP/Druid)
 *        ↓ wrapped by
 *   Seata DataSourceProxy
 *        ↓ registered to
 *   DynamicRoutingDataSource (通过 addDataSource)
 * </pre>
 *
 * <p>需配合 {@code SeataAutoConfiguration} 的 {@code @ConditionalOnMissingBean} 使用，
 * 不覆盖 ydsz-common-jdbc 原生 {@link DynamicRoutingDataSource}。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public class SeataDynamicDataSourceAdapter extends DynamicRoutingDataSource {

    /** 日志实例 */
    private static final Logger LOG = LoggerFactory.getLogger(SeataDynamicDataSourceAdapter.class);

    /**
     * 重写父类方法，注册数据源时自动包装为 Seata DataSourceProxy。
     *
     * <p>物理数据源会被包装为 {@link DataSourceProxy}，使 Seata AT 模式能够拦截 SQL 生成 undo_log。
     * 动态路由能力由父类 {@link DynamicRoutingDataSource} 保留。
     *
     * @param key 数据源键（如 MASTER/SLAVE 或自定义名称）
     * @param dataSource 物理数据源实例
     */
    @Override
    public void addDataSource(Object key, DataSource dataSource) {
        if (dataSource == null) {
            super.addDataSource(key, null);
            LOG.warn("registering null DataSource for key: {}", key);
            return;
        }
        // 已经是 DataSourceProxy 的不再重复包装
        if (dataSource instanceof DataSourceProxy) {
            super.addDataSource(key, dataSource);
            LOG.info("Dynamic DataSource registered (already proxied): {}", key);
            return;
        }
        DataSource proxy = new DataSourceProxy(dataSource);
        super.addDataSource(key, proxy);
        LOG.info("Dynamic DataSource registered with Seata proxy: {}", key);
    }
}
