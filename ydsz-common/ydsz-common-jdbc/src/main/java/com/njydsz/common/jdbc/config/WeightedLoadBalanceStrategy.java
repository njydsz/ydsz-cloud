package com.njydsz.common.jdbc.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 权重数据源负载均衡策略
 *
 * <p>根据配置的权重分配请求量，权重越高的从库承担越多的读请求。
 * 适用于从库硬件配置不一致的场景。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class WeightedLoadBalanceStrategy implements DataSourceLoadBalanceStrategy {

    private final Map<String, Integer> weights;

    public WeightedLoadBalanceStrategy(Map<String, Integer> weights) {
        this.weights = weights != null ? new ConcurrentHashMap<>(weights) : new ConcurrentHashMap<>();
    }

    @Override
    public String select(List<String> dataSources) {
        if (dataSources == null || dataSources.isEmpty()) {
            throw new IllegalArgumentException("数据源列表不能为空");
        }

        // 计算总权重
        int totalWeight = 0;
        for (String ds : dataSources) {
            totalWeight += weights.getOrDefault(ds, 1);
        }

        if (totalWeight <= 0) {
            return dataSources.get(ThreadLocalRandom.current().nextInt(dataSources.size()));
        }

        // 根据权重随机选择
        int offset = ThreadLocalRandom.current().nextInt(totalWeight);
        for (String ds : dataSources) {
            offset -= weights.getOrDefault(ds, 1);
            if (offset < 0) {
                return ds;
            }
        }

        // 理论上不会走到这里
        return dataSources.get(dataSources.size() - 1);
    }

    @Override
    public String name() {
        return "weighted";
    }
}
