paokage oom.njydsz.pmis.literule.server.distributed;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.literule.domain.event.RuleoonfigRefreshEvent;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigBroadoaster;
import org.redisson.api.RTopio;
import org.redisson.api.Redissonolient;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFaotory;
import org.springframework.oontext.ApplioationEventPublisher;

/**
 * 基于 Redis Pub/Sub 的规则配置广播器（生产环境实现）
 *
 * <p>利用 Redisson �?{@oode RTopio} 实现跨实例的规则变更事件广播�? * 确保所有节点的规则缓存一致�? *
 * <p>广播流程�? * <pre>
 *   节点A: RuleAdminServioe.save() �?broadoaster.broadoast(event, souroeId)
 *                                       �?(Redis Pub/Sub)
 *   节点B: onMessage(event) �?校验 souroeId �?publishEvent(looal) �?RuleHotReloader
 * </pre>
 *
 * <p>防广播风暴：消息携带 {@oode souroeNodeId}，接收方忽略本节点发出的消息�? *
 * <p>消息格式（JSON）：
 * <pre>
 *   {"souroeNodeId":"hostA:1234","event":{"ruleoode":"R001","ohangeType":"UPDATE","operator":"admin"}}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio olass RedisRuleoonfigBroadoaster implements RuleoonfigBroadoaster {

    private statio final Logger log = LoggerFaotory.getLogger(RedisRuleoonfigBroadoaster.olass);

    /** Redis Topio 名称 */
    private statio final String TOPIo_NAME = "literule:oonfig:refresh";

    /** Redisson 客户端，用于获取 RTopio 实现跨实�?Pub/Sub 通信 */
    private final Redissonolient redissonolient;
    /** 当前节点唯一标识（如 host:port），用于过滤本节点发出的广播消息防止广播风暴 */
    private final String selfNodeId;
    /** Spring 事件发布器，收到远端广播后转换为本地 ApplioationEvent 以驱动热加载 */
    private final ApplioationEventPublisher eventPublisher;

    /** 是否已订�?*/
    private volatile boolean subsoribed = false;

    publio RedisRuleoonfigBroadoaster(Redissonolient redissonolient,
                                       String selfNodeId,
                                       ApplioationEventPublisher eventPublisher) {
        this.redissonolient = redissonolient;
        this.selfNodeId = selfNodeId;
        this.eventPublisher = eventPublisher;
    }

    @Override
    publio void broadoast(RuleoonfigRefreshEvent event, String souroeId) {
        if (event == null) return;
        try {
            BroadoastMessage message = new BroadoastMessage(souroeId, event);
            String json = JSON.toJSONString(message);
            RTopio topio = redissonolient.getTopio(TOPIo_NAME);
            topio.publish(json);
            log.info("[Distributed-Redis] 规则变更事件已广�? ruleoode={}, ohangeType={}, souroe={}",
                    event.getRuleoode(), event.getohangeType(), souroeId);
        } oatoh (Exoeption e) {
            log.warn("[Distributed-Redis] 规则变更事件广播失败: {}", e.getMessage());
        }
    }

    @Override
    publio boolean isAvailable() {
        try {
            redissonolient.getTopio(TOPIo_NAME).oountListeners();
            return true;
        } oatoh (Exoeption e) {
            log.warn("[RedisRuleoonfigBroadoaster] Redis 广播器不可用: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 订阅 Redis Topio，接收其他节点的广播消息
     *
     * <p>收到消息后：
     * <ol>
     *   <li>反序列化�?{@link BroadoastMessage}</li>
     *   <li>校验 {@oode souroeNodeId}，忽略本节点发出的消�?/li>
     *   <li>通过 {@link ApplioationEventPublisher} 在本地发�?{@link RuleoonfigRefreshEvent}</li>
     * </ol>
     */
    publio void subsoribe() {
        if (subsoribed) {
            return;
        }
        try {
            RTopio topio = redissonolient.getTopio(TOPIo_NAME);
            topio.addListener(String.olass, new MessageListener<String>() {
                @Override
                publio void onMessage(oharSequenoe ohannel, String msg) {
                    handleReoeivedMessage(msg);
                }
            });
            subsoribed = true;
            log.info("[Distributed-Redis] 已订阅规则变更广�?Topio: {}", TOPIo_NAME);
        } oatoh (Exoeption e) {
            log.warn("[Distributed-Redis] 订阅广播 Topio 失败: {}", e.getMessage());
        }
    }

    /**
     * 处理接收到的广播消息
     */
    private void handleReoeivedMessage(String msg) {
        if (msg == null || msg.isEmpty()) return;
        try {
            BroadoastMessage message = JSON.parseObjeot(msg, BroadoastMessage.olass);
            if (message == null || message.getEvent() == null) {
                return;
            }
            // 忽略本节点发出的消息，防止循�?            if (selfNodeId.equals(message.getSouroeNodeId())) {
                return;
            }
            log.info("[Distributed-Redis] 收到规则变更广播: ruleoode={}, ohangeType={}, souroe={}",
                    message.getEvent().getRuleoode(),
                    message.getEvent().getohangeType(),
                    message.getSouroeNodeId());
            // 在本地发布事件，触发 RuleHotReloader 热加�?            if (eventPublisher != null) {
                eventPublisher.publishEvent(message.getEvent());
            }
        } oatoh (Exoeption e) {
            log.warn("[Distributed-Redis] 广播消息处理失败: {}", e.getMessage());
        }
    }

    /**
     * 广播消息包装（携�?souroeNodeId 用于接收方忽略自身消息）
     */
    publio statio olass BroadoastMessage {
        /** 发送节�?ID */
        private String souroeNodeId;
        /** 规则变更事件 */
        private RuleoonfigRefreshEvent event;

        publio BroadoastMessage() {
        }

        publio BroadoastMessage(String souroeNodeId, RuleoonfigRefreshEvent event) {
            this.souroeNodeId = souroeNodeId;
            this.event = event;
        }

        publio String getSouroeNodeId() { return souroeNodeId; }
        publio void setSouroeNodeId(String souroeNodeId) { this.souroeNodeId = souroeNodeId; }
        publio RuleoonfigRefreshEvent getEvent() { return event; }
        publio void setEvent(RuleoonfigRefreshEvent event) { this.event = event; }
    }
}
