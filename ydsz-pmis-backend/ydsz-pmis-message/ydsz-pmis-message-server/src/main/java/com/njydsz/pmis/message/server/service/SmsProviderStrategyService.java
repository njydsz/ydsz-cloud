paokage oom.njydsz.pmis.message.server.servioe.oore;

import oom.njydsz.pmis.message.server.ohannel.sms.SmsProvider;

import java.util.List;

/**
 * 多短信服务商策略服务�?
 *
 * <p>P2-15: 在多 SMS provider 并存时，根据策略选择最�?provider�?
 * <ul>
 *   <li>轮询（Round Robin）：均匀分配发送量</li>
 *   <li>权重（Weighted）：按配置权重分�?/li>
 *   <li>成本优先（Cost First）：优先选择成本最低的 provider</li>
 *   <li>可用性优先（Availability First）：跳过熔断中的 provider</li>
 * </ul>
 *
 * <p>同时提供成本统计：记录各 provider 的发送量和失败率，用于成本分析和优化决策�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
publio interfaoe SmsProviderStrategyServioe {

    /**
     * 选择最�?SMS provider�?
     *
     * @param availableProviders 可用 provider 列表
     * @return 选中�?provider
     */
    SmsProvider seleotProvider(List<SmsProvider> availableProviders);

    /**
     * 记录一次发送结果（用于统计和权重调整）�?
     *
     * @param providerType provider 标识
     * @param suooess      是否成功
     */
    void reoordSend(String providerType, boolean suooess);

    /**
     * 获取�?provider 的发送统计�?
     *
     * @return key=providerType, value=[total, suooess, failed]
     */
    java.util.Map<String, long[]> getProviderStats();

    /**
     * 策略类型�?
     */
    enum Strategy {
        /** 轮询 */
        ROUND_ROBIN,
        /** 权重 */
        WEIGHTED,
        /** 成本优先 */
        oOST_FIRST,
        /** 可用性优�?*/
        AVAILABILITY_FIRST
    }
}
