package com.njydsz.pmis.common.redis.service.ops;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis Pub/Sub 操作组件
 *
 * <p>提供发布/订阅模式操作，包括：
 * <ul>
 *   <li>频道发布（publish）</li>
 *   <li>频道订阅（subscribe）</li>
 *   <li>模式订阅（pattern subscribe）</li>
 *   <li>取消订阅（unsubscribe）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPubSubOps {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ConcurrentHashMap<String, MessageListener> listenerMap = new ConcurrentHashMap<>();

    // ============================ 发布操作 =============================

    /**
     * 向指定频道发布消息
     *
     * @param channel 频道名
     * @param message 消息内容
     * @return 接收到消息的客户端数量
     */
    public long publish(String channel, Object message) {
        if (channel == null || channel.isEmpty()) {
            log.warn("【Redis】PUBLISH 操作失败：频道名不能为空");
            return 0;
        }
        try {
            Long result = redisTemplate.convertAndSend(channel, message);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("【Redis】PUBLISH 操作失败 | channel={} | error={}", channel, e.getMessage());
            return 0;
        }
    }

    // ============================ 订阅操作 =============================

    /**
     * 订阅指定频道
     *
     * @param channel  频道名
     * @param listener 消息监听器
     * @return 订阅 ID，用于取消订阅
     */
    public String subscribe(String channel, Consumer<PubSubMessage> listener) {
        if (channel == null || channel.isEmpty()) {
            log.warn("【Redis】SUBSCRIBE 操作失败：频道名不能为空");
            return null;
        }
        if (listener == null) {
            log.warn("【Redis】SUBSCRIBE 操作失败：监听器不能为空");
            return null;
        }
        if (listenerContainer == null) {
            log.warn("【Redis】SUBSCRIBE 操作失败：RedisMessageListenerContainer 未配置");
            return null;
        }
        try {
            String subscriptionId = UUID.randomUUID().toString();
            MessageListener springListener = (message, pattern) -> listener.accept(toPubSubMessage(message, pattern));
            listenerMap.put(subscriptionId, springListener);
            listenerContainer.addMessageListener(springListener, new ChannelTopic(channel));
            log.info("【Redis】订阅频道成功 | channel={} | subscriptionId={}", channel, subscriptionId);
            return subscriptionId;
        } catch (Exception e) {
            log.error("【Redis】SUBSCRIBE 操作失败 | channel={} | error={}", channel, e.getMessage());
            return null;
        }
    }

    /**
     * 模式订阅（支持通配符）
     *
     * <p>支持的模式通配符：
     * <ul>
     *   <li>{@code *} - 匹配任意数量字符</li>
     *   <li>{@code ?} - 匹配单个字符</li>
     *   <li>{@code [ab]} - 匹配 a 或 b</li>
     * </ul>
     *
     * @param pattern  模式，如 "news.*"
     * @param listener 消息监听器
     * @return 订阅 ID，用于取消订阅
     */
    public String patternSubscribe(String pattern, Consumer<PubSubMessage> listener) {
        if (pattern == null || pattern.isEmpty()) {
            log.warn("【Redis】PSUBSCRIBE 操作失败：模式不能为空");
            return null;
        }
        if (listener == null) {
            log.warn("【Redis】PSUBSCRIBE 操作失败：监听器不能为空");
            return null;
        }
        if (listenerContainer == null) {
            log.warn("【Redis】PSUBSCRIBE 操作失败：RedisMessageListenerContainer 未配置");
            return null;
        }
        try {
            String subscriptionId = UUID.randomUUID().toString();
            MessageListener springListener = (message, p) -> listener.accept(toPubSubMessage(message, p));
            listenerMap.put(subscriptionId, springListener);
            listenerContainer.addMessageListener(springListener, new PatternTopic(pattern));
            log.info("【Redis】模式订阅成功 | pattern={} | subscriptionId={}", pattern, subscriptionId);
            return subscriptionId;
        } catch (Exception e) {
            log.error("【Redis】PSUBSCRIBE 操作失败 | pattern={} | error={}", pattern, e.getMessage());
            return null;
        }
    }

    // ============================ 取消订阅操作 =============================

    /**
     * 取消订阅
     *
     * @param subscriptionId 订阅 ID（由 subscribe 返回）
     * @param topic          频道名
     */
    public void unsubscribe(String subscriptionId, String topic) {
        if (subscriptionId == null || topic == null || listenerContainer == null) {
            return;
        }
        try {
            MessageListener listener = listenerMap.remove(subscriptionId);
            if (listener != null) {
                listenerContainer.removeMessageListener(listener, new ChannelTopic(topic));
                log.info("【Redis】取消订阅成功 | topic={} | subscriptionId={}", topic, subscriptionId);
            }
        } catch (Exception e) {
            log.error("【Redis】取消订阅失败 | topic={} | subscriptionId={} | error={}", topic, subscriptionId, e.getMessage());
        }
    }

    /**
     * 取消模式订阅
     *
     * @param subscriptionId 订阅 ID（由 patternSubscribe 返回）
     * @param pattern        模式
     */
    public void patternUnsubscribe(String subscriptionId, String pattern) {
        if (subscriptionId == null || pattern == null || listenerContainer == null) {
            return;
        }
        try {
            MessageListener listener = listenerMap.remove(subscriptionId);
            if (listener != null) {
                listenerContainer.removeMessageListener(listener, new PatternTopic(pattern));
                log.info("【Redis】取消模式订阅成功 | pattern={} | subscriptionId={}", pattern, subscriptionId);
            }
        } catch (Exception e) {
            log.error("【Redis】取消模式订阅失败 | pattern={} | subscriptionId={} | error={}", pattern, subscriptionId, e.getMessage());
        }
    }

    // ============================ 内部辅助方法 =============================

    private PubSubMessage toPubSubMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String patternStr = pattern != null ? new String(pattern, StandardCharsets.UTF_8) : null;
        Object body = redisTemplate.getValueSerializer().deserialize(message.getBody());
        return new PubSubMessage(channel, patternStr, body);
    }

    /**
     * Pub/Sub 消息封装
     */
    public static class PubSubMessage {
        private final String channel;
        private final String pattern;
        private final Object body;

        public PubSubMessage(String channel, String pattern, Object body) {
            this.channel = channel;
            this.pattern = pattern;
            this.body = body;
        }

        public String getChannel() {
            return channel;
        }

        public String getPattern() {
            return pattern;
        }

        public Object getBody() {
            return body;
        }

        public <T> T getBody(Class<T> clazz) {
            if (body == null) {
                return null;
            }
            if (clazz.isInstance(body)) {
                return clazz.cast(body);
            }
            return null;
        }
    }
}
