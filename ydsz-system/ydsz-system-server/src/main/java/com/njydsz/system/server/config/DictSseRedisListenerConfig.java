package com.njydsz.system.server.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.thread.util.ExecutorUtils;
import com.njydsz.system.server.service.event.DictChangeEvent;
import com.njydsz.system.server.service.event.DictChangeEventConstants;

@Slf4j
@Configuration
@ConditionalOnClass({RedisMessageListenerContainer.class, StringRedisTemplate.class})
@ConditionalOnProperty(prefix = "ydsz.system.sse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DictSseRedisListenerConfig implements DisposableBean {

  private final Map<RedisMessageListenerContainer, ScheduledExecutorService> containerTaskMap =
      new ConcurrentHashMap<>();

  @Bean
  public DictSseEmitterRegistry dictSseEmitterRegistry() {
    return new DictSseEmitterRegistry();
  }

  @Bean
  public RedisMessageListenerContainer dictSseRedisContainer(
      ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
      DictSseEmitterRegistry emitterRegistry,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider) {

    RedisConnectionFactory factory = connectionFactoryProvider.getIfAvailable();
    if (factory == null) {
      log.info("[DictSseRedisListener] RedisConnectionFactory not available, SSE disabled");
      return null;
    }

    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(factory);

    MessageListenerAdapter listener = new MessageListenerAdapter(
        new DictSseMessageDelegate(emitterRegistry));
    listener.setSerializer(null);
    listener.setDefaultListenerMethod("onMessage");

    container.addMessageListener(listener,
        new PatternTopic(DictChangeEventConstants.REDIS_CHANNEL));

    container.setErrorHandler(e ->
        log.warn("[DictSseRedisListener] Redis listener error: {}", e.getMessage()));

    log.info("[DictSseRedisListener] Subscribed to channel: {}",
        DictChangeEventConstants.REDIS_CHANNEL);

    ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(
        1, ExecutorUtils.createDaemonThreadFactory("dict-sse-keepalive"));
    scheduler.scheduleAtFixedRate(
        emitterRegistry::broadcastKeepalive,
        DictChangeEventConstants.SSE_KEEPALIVE_INTERVAL_SECONDS,
        DictChangeEventConstants.SSE_KEEPALIVE_INTERVAL_SECONDS,
        TimeUnit.SECONDS);

    containerTaskMap.put(container, scheduler);
    return container;
  }

  @Override
  public void destroy() {
    for (Map.Entry<RedisMessageListenerContainer, ScheduledExecutorService> entry :
        containerTaskMap.entrySet()) {
      entry.getValue().shutdownNow();
      try {
        entry.getKey().stop();
      } catch (Exception e) {
        log.debug("[DictSseRedisListener] Error stopping container: {}", e.getMessage());
      }
    }
    containerTaskMap.clear();
  }

  /**
   * Redis 消息委托器 — 反序列化 JSON 事件并广播到 SSE Emitter。
   */
  public static class DictSseMessageDelegate {

    private final DictSseEmitterRegistry registry;

    public DictSseMessageDelegate(DictSseEmitterRegistry registry) {
      this.registry = registry;
    }

    /**
     * 由 {@link MessageListenerAdapter} 反射调用，接收原始 String 消息。
     *
     * @param message Redis Pub/Sub 原始消息体（JSON 字符串）
     */
    public void onMessage(String message) {
      try {
        DictChangeEvent event = YdszJson.fromJson(message, DictChangeEvent.class);
        if (event == null || event.getDictCode() == null) {
          log.warn("[DictSseRedisListener] Malformed event received, ignored");
          return;
        }
        log.debug("[DictSseRedisListener] Received dict change: dictCode={}, eventType={}",
            event.getDictCode(), event.getEventType());
        registry.broadcastEvent(message);
      } catch (Exception e) {
        log.warn("[DictSseRedisListener] Failed to process Redis message: err={}",
            e.getMessage());
      }
    }
  }
}