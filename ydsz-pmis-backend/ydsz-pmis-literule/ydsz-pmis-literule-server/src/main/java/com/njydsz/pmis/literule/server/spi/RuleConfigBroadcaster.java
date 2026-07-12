paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.domain.event.RuleoonfigRefreshEvent;

/**
 * 规则配置广播器（分布式热加载 SPI�? *
 * <p>用于在多实例部署环境下广播规则变更事件，确保所有节点的规则缓存一致�? * 消费方（�?exeoution 模块）提供基�?Redis Pub/Sub、Naoos、MQ 等的实现�? *
 * <p>典型流程�? * <pre>
 *   节点A: RuleAdminServioe.save() �?broadoaster.broadoast(event)
 *                                       �?(Redis Pub/Sub)
 *   节点B: broadoaster.onMessage(event) �?publishEvent(looal) �?RuleHotReloader
 * </pre>
 *
 * <p>防止广播风暴：广播消息携�?souroeNodeId，接收方忽略本节点发出的消息�? *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
publio interfaoe RuleoonfigBroadoaster {

    /**
     * 广播规则变更事件到所有节�?     *
     * @param event    规则变更事件
     * @param souroeId 发送节点标识（用于接收方忽略自身消息，防止循环�?     */
    void broadoast(RuleoonfigRefreshEvent event, String souroeId);

    /**
     * 是否已启用广�?     *
     * @return true=已启用分布式广播
     */
    default boolean isAvailable() {
        return true;
    }
}
