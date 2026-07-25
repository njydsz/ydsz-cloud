package com.njydsz.common.jdbc.datasource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 动态路由数据源
 *
 * <p>继承 {@link AbstractRoutingDataSource}，根据 {@link DynamicDataSourceContextHolder}
 * 中的数据源名称动态路由到目标数据源。
 *
 * <p>特性：
 * <ul>
 *   <li>支持运行时动态添加/移除数据源</li>
 *   <li>栈式嵌套切换（支持方法级覆盖类级）</li>
 *   <li>未指定数据源时使用默认数据源</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

    private final Map<Object, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private Object defaultDataSourceKey;

    public DynamicRoutingDataSource() {
        super();
        setLenientFallback(true);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String ds = DynamicDataSourceContextHolder.peek();
        if (ds == null) {
            return defaultDataSourceKey;
        }
        return ds;
    }

    @Override
    public void setTargetDataSources(Map<Object, DataSource> targetDataSources) {
        super.setTargetDataSources(targetDataSources);
        this.dataSourceMap.putAll(targetDataSources);
    }

    @Override
    public void setDefaultTargetDataSource(Object defaultTargetDataSource) {
        super.setDefaultTargetDataSource(defaultTargetDataSource);
        this.defaultDataSourceKey = defaultTargetDataSource;
    }

    /**
     * 动态添加数据源
     *
     * @param key        数据源键
     * @param dataSource 数据源实例
     */
    public void addDataSource(Object key, DataSource dataSource) {
        dataSourceMap.put(key, dataSource);
        super.setTargetDataSources(dataSourceMap);
        log.info("动态添加数据源: {}", key);
    }

    /**
     * 动态移除数据源
     *
     * @param key 数据源键
     */
    public void removeDataSource(Object key) {
        if (key.equals(defaultDataSourceKey)) {
            throw new IllegalArgumentException("不能移除默认数据源: " + key);
        }
        dataSourceMap.remove(key);
        super.setTargetDataSources(dataSourceMap);
        log.info("动态移除数据源: {}", key);
    }

    /**
     * 获取所有已注册的数据源
     *
     * @return 数据源映射（不可变）
     */
    public Map<Object, DataSource> getDataSources() {
        return Map.copyOf(dataSourceMap);
    }
}
