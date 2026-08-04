package com.njydsz.common.jdbc.datasource;

import java.util.HashMap;
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

    /**
     * 重写父类方法，同步维护内部 {@link #dataSourceMap}。
     *
     * <p>注意：Spring 6+ 中父类 {@code setTargetDataSources} 签名为
     * {@code Map<Object, Object>}，此处保持签名一致以正确覆盖。
     *
     * @param targetDataSources 目标数据源映射（value 实际为 {@link DataSource} 类型）
     */
    @Override
    public void setTargetDataSources(Map<Object, Object> targetDataSources) {
        super.setTargetDataSources(targetDataSources);
        if (targetDataSources != null) {
            targetDataSources.forEach((k, v) -> {
                if (v instanceof DataSource) {
                    this.dataSourceMap.put(k, (DataSource) v);
                }
            });
        }
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
        super.setTargetDataSources(castToTargetMap(dataSourceMap));
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
        super.setTargetDataSources(castToTargetMap(dataSourceMap));
        log.info("动态移除数据源: {}", key);
    }

    /**
     * 将 {@code Map<Object, DataSource>} 安全转换为父类要求的 {@code Map<Object, Object>}。
     *
     * <p>通过新建 {@code HashMap<Object, Object>} 装载原 Map 的 entry，利用 Java 泛型
     * 协变特性（{@code DataSource} 是 {@code Object} 的子类）避免 unchecked 警告。
     *
     * @param source 原始数据源映射
     * @return 父类要求的 Map 形式
     */
    private static Map<Object, Object> castToTargetMap(Map<Object, DataSource> source) {
        Map<Object, Object> result = new HashMap<>(source.size());
        for (Map.Entry<Object, DataSource> entry : source.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
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
