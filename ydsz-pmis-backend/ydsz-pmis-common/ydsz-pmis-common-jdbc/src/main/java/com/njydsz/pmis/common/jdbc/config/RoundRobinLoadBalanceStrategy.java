package com.njydsz.pmis.common.jdbc.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询数据源负载均衡策略
 *
 * <p>按顺序轮流选择从库，保证每个从库的请求量均匀分布。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public class RoundRobinLoadBalanceStrategy implements DataSourceLoadBalanceStrategy {

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public String select(List<String> dataSources) {
        if (dataSources == null || dataSources.isEmpty()) {
            throw new IllegalArgumentException("数据源列表不能为空");
        }
        int currentIndex = index.getAndIncrement();
        return dataSources.get(Math.abs(currentIndex) % dataSources.size());
    }

    @Override
    public String name() {
        return "round-robin";
    }
}
