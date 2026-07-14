ackage com.njydsz.pmis.literule.server.distributed;

import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import com.njydsz.pmis.common.json.YdszJson;

import com.njydsz.pmis.literule.domain.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.server.spi.RuleConfigBroadcaster;

/**
 * 基于 Redis Pub/Sub 的规则配置广播器（生产环境实现）
 *
 * <p>利用 Redisson 的 {@code RTopic} 实现跨实例的规则变更事件广播，
 * 确保所有节点的规则缓存一致。
 *
 * <p>广播流程：
 * <pre>
 *   节点A: RuleAdminService.save() → broadcaster.broadcast(event, sourceId)
 *                                       ↓ (Redis Pub/Sub)
 *   节点B: onMessage(event) → 校验 sourceId → publishEvent(local) → RuleHotReloader
 * </pre>
 *
 * <p>防广播风暴：消息携带 {@code sourceNodeId}，接收方忽略本节点发出的消息。
 *
 * <p>消息格式（JSON）：
 * <pre>
 *   {"sourceNodeId":"hostA:1234","event":{"ruleCode":"R001","changeType":"UPDATE","operator":"admin"}}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class RedisRuleConfigBroadcaster implements RuleConfigBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RedisRuleConfigBroadcaster.class);

    /** Redis Topic 名称 */
    private static final String TOPIC_NAME = "literule:config:refresh";

    /** Redisson 客户端，用于获取 RTopic 实现跨实例 Pub/Sub 通信 */
    private final RedissonClient redissonClient;
    /** 当前节点唯一标识（如 host:port），用于过滤本节点发出的广播消息防止广播风暴 */
    private final String selfNodeId;
    /** Spring 事件发布器，收到远端广播后转换为本地 ApplicationEvent 以驱动热加载 */
    private final ApplicationEventPublisher eventPublisher;

    /** 是否已订阅 */
    private volatile boolean subscribed = false;

    public RedisRuleConfigBroadcaster(RedissonClient redissonClient,
                                       String selfNodeId,
                                       ApplicationEventPublisher eventPublisher) {
        this.redissonClient = redissonClient;
        this.selfNodeId = selfNodeId;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void broadcast(RuleConfigRefreshEvent event, String sourceId) {
        if (event == null) return;
        try {
            BroadcastMessage message = new BroadcastMessage(sourceId, event);
            String json = YdszJson.toJson(message);
            RTopic topic = redissonClient.getTopic(TOPIC_NAME);
            topic.publish(json);
            log.info("[Distributed-Redis] 规则变更事件已广播: ruleCode={}, changeType={}, source={}",
                    event.getRuleCode(), event.getChangeType(), sourceId);
        } catch (Exception e) {
            log.warn("[Distributed-Redis] 规则变更事件广播失败: {}", e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            redissonClient.getTopic(TOPIC_NAME).countListeners();
            return true;
        } catch (Exception e) {
            log.warn("[RedisRuleConfigBroadcaster] Redis 广播器不可用: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 订阅 Redis Topic，接收其他节点的广播消息
     *
     * <p>收到消息后：
     * <ol>
     *   <li>反序列化为 {@link BroadcastMessage}</li>
     *   <li>校验 {@code sourceNodeId}，忽略本节点发出的消息</li>
     *   <li>通过 {@link ApplicationEventPublisher} 在本地发布 {@link RuleConfigRefreshEvent}</li>
     * </ol>
     */
    public void subscribe() {
        if (subscribed) {
            return;
        }
        try {
            RTopic topic = redissonClient.getTopic(TOPIC_NAME);
            topic.addListener(String.class, new MessageListener<String>() {
                @Override
                public void onMessage(CharSequence channel, String msg) {
                    handleReceivedMessage(msg);
                }
            });
            subscribed = true;
            log.info("[Distributed-Redis] 已订阅规则变更广播 Topic: {}", TOPIC_NAME);
        } catch (Exception e) {
            log.warn("[Distributed-Redis] 订阅广播 Topic 失败: {}", e.getMessage());
        }
    }

    /**
     * 处理接收到的广播消息
     */
    private void handleReceivedMessage(String msg) {
        if (msg == null || msg.isEmpty()) return;
        try {
            BroadcastMessage message = YdszJson.toObject(msg, BroadcastMessage.class);
            if (message == null || message.getEvent() == null) {
                return;
            }
            // 忽略本节点发出的消息，防止循环
            if (selfNodeId.equals(message.getSourceNodeId())) {
                return;
            }
            log.info("[Distributed-Redis] 收到规则变更广播: ruleCode={}, changeType={}, source={}",
                    message.getEvent().getRuleCode(),
                    message.getEvent().getChangeType(),
                    message.getSourceNodeId());
            // 在本地发布事件，触发 RuleHotReloader 热加载
            if (eventPublisher != null) {
                eventPublisher.publishEvent(message.getEvent());
            }
        } catch (Exception e) {
            log.warn("[Distributed-Redis] 广播消息处理失败: {}", e.getMessage());
        }
    }

    /**
     * 广播消息包装（携带 sourceNodeId 用于接收方忽略自身消息）
     */
    public static class BroadcastMessage {
        /** 发送节点 ID */
        private String sourceNodeId;
        /** 规则变更事件 */
        private RuleConfigRefreshEvent event;

        public BroadcastMessage() {
        }

        public BroadcastMessage(String sourceNodeId, RuleConfigRefreshEvent event) {
            this.sourceNodeId = sourceNodeId;
            this.event = event;
        }

        public String getSourceNodeId() { return sourceNodeId; }
        public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }
        public RuleConfigRefreshEvent getEvent() { return event; }
        public void setEvent(RuleConfigRefreshEvent event) { this.event = event; }
    }
}
