package com.njydsz.pmis.common.jdbc.config;

import java.util.List;

/**
 * 数据源负载均衡策略接口
 *
 * <p>在读写分离场景下，用于从多个从库中选择一个进行读操作。
 * 提供多种策略实现：轮询、随机、权重。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
public interface DataSourceLoadBalanceStrategy {

    /**
     * 从候选数据源列表中选择一个
     *
     * @param dataSources 候选数据源名称列表
     * @return 选中的数据源名称
     */
    String select(List<String> dataSources);

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    String name();
}
