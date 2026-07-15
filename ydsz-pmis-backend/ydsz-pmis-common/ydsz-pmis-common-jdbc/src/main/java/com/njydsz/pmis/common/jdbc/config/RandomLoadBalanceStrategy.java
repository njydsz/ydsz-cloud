package com.njydsz.pmis.common.jdbc.config;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机数据源负载均衡策略
 *
 * <p>从候选从库中随机选择一个，适用于从库配置差异不大的场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class RandomLoadBalanceStrategy implements DataSourceLoadBalanceStrategy {

    @Override
    public String select(List<String> dataSources) {
        if (dataSources == null || dataSources.isEmpty()) {
            throw new IllegalArgumentException("数据源列表不能为空");
        }
        return dataSources.get(ThreadLocalRandom.current().nextInt(dataSources.size()));
    }

    @Override
    public String name() {
        return "random";
    }
}
