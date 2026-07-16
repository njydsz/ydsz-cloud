package com.njydsz.message.server.service.core;

import java.util.List;
import java.util.Map;

import com.njydsz.message.server.channel.sms.SmsProvider;

/**
 * 多短信服务商策略服务。
 *
 * <p>P2-15: 在多 SMS provider 并存时，根据策略选择最优 provider：
 * <ul>
 *   <li>轮询（Round Robin）：均匀分配发送量</li>
 *   <li>权重（Weighted）：按配置权重分配</li>
 *   <li>成本优先（Cost First）：优先选择成本最低的 provider</li>
 *   <li>可用性优先（Availability First）：跳过熔断中的 provider</li>
 * </ul>
 *
 * <p>同时提供成本统计：记录各 provider 的发送量和失败率，用于成本分析和优化决策。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public interface SmsProviderStrategyService {

    /**
     * 选择最优 SMS provider。
     *
     * @param availableProviders 可用 provider 列表
     * @return 选中的 provider
     */
    SmsProvider selectProvider(List<SmsProvider> availableProviders);

    /**
     * 记录一次发送结果（用于统计和权重调整）。
     *
     * @param providerType provider 标识
     * @param success      是否成功
     */
    void recordSend(String providerType, boolean success);

    /**
     * 获取各 provider 的发送统计。
     *
     * @return key=providerType, value=[total, success, failed]
     */
    Map<String, long[]> getProviderStats();

    /**
     * 策略类型。
     */
    enum Strategy {
        /** 轮询 */
        ROUND_ROBIN,
        /** 权重 */
        WEIGHTED,
        /** 成本优先 */
        COST_FIRST,
        /** 可用性优先 */
        AVAILABILITY_FIRST
    }
}
