package com.njydsz.common.socket.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.socket.ack.MessageAckService;
import com.njydsz.common.socket.audit.WebSocketAuditService;
import com.njydsz.common.socket.auth.WebSocketAuthInterceptor;
import com.njydsz.common.socket.cluster.WebSocketClusterMessage;
import com.njydsz.common.socket.cluster.WebSocketClusterPublisher;
import com.njydsz.common.socket.compress.MessageCompressor;
import com.njydsz.common.socket.filter.MessageFilter;
import com.njydsz.common.socket.heartbeat.WebSocketHeartbeatHandler;
import com.njydsz.common.socket.health.WebSocketHealthIndicator;
import com.njydsz.common.socket.interceptor.StompMessageInterceptor;
import com.njydsz.common.socket.lifecycle.WebSocketConnectionListener;
import com.njydsz.common.socket.metric.WebSocketMetrics;
import com.njydsz.common.socket.monitor.SlowConnectionDetector;
import com.njydsz.common.socket.offline.OfflineMessageStore;
import com.njydsz.common.socket.offline.RedisOfflineMessageStore;
import com.njydsz.common.socket.push.DefaultRealtimePushTemplate;
import com.njydsz.common.socket.push.RealtimePushTemplate;
import com.njydsz.common.socket.ratelimit.ConnectionLimiter;
import com.njydsz.common.socket.ratelimit.WebSocketRateLimiter;
import com.njydsz.common.socket.retry.DeadLetterQueue;
import com.njydsz.common.socket.retry.MessageRetryQueue;
import com.njydsz.common.socket.retry.RedisDeadLetterQueue;
import com.njydsz.common.socket.retry.RedisMessageRetryQueue;
import com.njydsz.common.socket.retry.RetryableMessage;
import com.njydsz.common.socket.resilience.WebSocketCircuitBreaker;
import com.njydsz.common.socket.serialize.JsonMessageSerializer;
import com.njydsz.common.socket.serialize.MessageSerializer;
import com.njydsz.common.socket.session.OnlineUserService;
import com.njydsz.common.socket.session.WebSocketSessionEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 自动装配配置。
 *
 * <p>当 classpath 存在 {@link SimpMessagingTemplate} 且 {@code ydsz.websocket.enabled=true} 时自动生效。
 *
 * <p>自动注册以下 Bean（按依赖顺序）：
 * <ul>
 *   <li>{@link WebSocketCircuitBreaker} — 熔断降级保护器（P0-2）</li>
 *   <li>{@link WebSocketAuditService} — 审计日志服务（P2-5）</li>
 *   <li>{@link MessageSerializer} — 消息序列化器（P3-5）</li>
 *   <li>{@link MessageCompressor} — 消息压缩器（P2-3）</li>
 *   <li>{@link SlowConnectionDetector} — 慢连接检测器（P2-2）</li>
 *   <li>{@link ConnectionLimiter} — 连接数限制器（P2-1）</li>
 *   <li>{@link WebSocketAuthInterceptor} — JWT 握手鉴权（P2-1 连接数检查 + P2-5 审计）</li>
 *   <li>{@link OnlineUserService} — 在线状态服务</li>
 *   <li>{@link OfflineMessageStore} — 离线消息存储（Redis 默认实现 + 熔断保护）</li>
 *   <li>{@link WebSocketHeartbeatHandler} — 心跳保活处理器（P0-3）</li>
 *   <li>{@link MessageAckService} — ACK 确认服务（P1-2）</li>
 *   <li>{@link MessageRetryQueue} — 消息重试队列（P0-4）</li>
 *   <li>{@link DeadLetterQueue} — 死信队列（P0-4）</li>
 *   <li>{@link WebSocketSessionEventListener} — 会话事件监听器</li>
 *   <li>{@link WebSocketMetrics} — Micrometer 指标</li>
 *   <li>{@link WebSocketRateLimiter} — 速率限制器</li>
 *   <li>{@link StompMessageInterceptor} — STOMP 消息拦截器（P3-1）</li>
 *   <li>{@link WebSocketHealthIndicator} — 健康检查（P0-1）</li>
 *   <li>{@link RealtimePushTemplate} — 统一推送模板</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(WebSocketProperties.class)
@ConditionalOnClass(SimpMessagingTemplate.class)
@ConditionalOnProperty(prefix = "ydsz.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketAutoConfiguration {

    // ==================== P0-2: 熔断降级 ====================

    /**
     * 创建熔断降级保护器 Bean。
     *
     * @param properties WebSocket 配置属性
     * @return 熔断器实例
     */
    @Bean
    @ConditionalOnMissingBean(WebSocketCircuitBreaker.class)
    public WebSocketCircuitBreaker webSocketCircuitBreaker(WebSocketProperties properties) {
        var cb = properties.getCircuitBreaker();
        return new WebSocketCircuitBreaker("WebSocketRedis",
                cb.getFailureRateThreshold(), cb.getSlidingWindowSize(),
                cb.getHalfOpenAfter().toMillis());
    }

    // ==================== P2-5: 审计日志 ====================

    /**
     * 创建审计日志服务 Bean。
     *
     * @return 审计日志服务实例
     */
    @Bean
    @ConditionalOnMissingBean(WebSocketAuditService.class)
    public WebSocketAuditService webSocketAuditService() {
        log.info("[WebSocket] 注册 WebSocketAuditService");
        return new WebSocketAuditService();
    }

    // ==================== P3-5: 消息序列化扩展点 ====================

    /**
     * 创建消息序列化器 Bean。
     *
     * @return JSON 消息序列化器实例
     */
    @Bean
    @ConditionalOnMissingBean(MessageSerializer.class)
    public MessageSerializer messageSerializer() {
        log.info("[WebSocket] 注册 JsonMessageSerializer");
        return new JsonMessageSerializer();
    }

    // ==================== P2-3: 消息压缩 ====================

    /**
     * 创建消息压缩器 Bean。
     *
     * @param properties WebSocket 配置属性
     * @return 消息压缩器实例
     */
    @Bean
    @ConditionalOnMissingBean(MessageCompressor.class)
    public MessageCompressor messageCompressor(WebSocketProperties properties) {
        log.info("[WebSocket] 注册 MessageCompressor (enabled={})", properties.getCompression().isEnabled());
        return new MessageCompressor(properties);
    }

    // ==================== P2-2: 慢连接检测 ====================

    @Bean
    @ConditionalOnMissingBean(SlowConnectionDetector.class)
    public SlowConnectionDetector slowConnectionDetector(
            WebSocketProperties properties, WebSocketMetrics metrics) {
        log.info("[WebSocket] 注册 SlowConnectionDetector (enabled={})", properties.getSlowConnection().isEnabled());
        return new SlowConnectionDetector(properties, metrics);
    }

    // ==================== P2-1: 连接数限制 ====================

    @Bean
    @ConditionalOnMissingBean(ConnectionLimiter.class)
    public ConnectionLimiter connectionLimiter(
            OnlineUserService onlineUserService,
            WebSocketProperties properties,
            WebSocketSessionEventListener eventListener) {
        log.info("[WebSocket] 注册 ConnectionLimiter (maxGlobal={}, maxPerUser={})",
                properties.getConnectionLimit().getMaxGlobalConnections(),
                properties.getConnectionLimit().getMaxPerUserConnections());
        return new ConnectionLimiter(onlineUserService, properties,
                eventListener.getActiveConnectionsCounter());
    }

    // ==================== 认证拦截器 ====================

    @Bean
    @ConditionalOnClass(TokenService.class)
    @ConditionalOnMissingBean(WebSocketAuthInterceptor.class)
    public WebSocketAuthInterceptor webSocketAuthInterceptor(
            TokenService tokenService,
            ConnectionLimiter connectionLimiter,
            WebSocketAuditService auditService) {
        log.info("[WebSocket] 注册 JWT 握手鉴权拦截器 (含连接数检查 + 审计)");
        return new WebSocketAuthInterceptor(tokenService, connectionLimiter, auditService);
    }

    // ==================== 在线用户状态 ====================

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(OnlineUserService.class)
    @ConditionalOnProperty(prefix = "ydsz.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OnlineUserService onlineUserService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            WebSocketProperties properties) {
        if (redisTemplate == null) {
            log.warn("[WebSocket] StringRedisTemplate 不存在，OnlineUserService 降级为 no-op");
            return new OnlineUserService(null, properties.getSessionTtlSeconds()) {
                @Override
                public void markOnline(String userId, String sessionId) { }
                @Override
                public void markOffline(String userId, String sessionId) { }
                @Override
                public boolean isOnline(String userId) { return false; }
                @Override
                public long getSessionCount(String userId) { return 0; }
                @Override
                public void renewSession(String userId, String sessionId) { }
            };
        }
        log.info("[WebSocket] 注册 OnlineUserService");
        return new OnlineUserService(redisTemplate, properties.getSessionTtlSeconds());
    }

    // ==================== 离线消息存储 ====================

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(OfflineMessageStore.class)
    @ConditionalOnProperty(prefix = "ydsz.websocket.offline", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OfflineMessageStore offlineMessageStore(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            WebSocketProperties properties,
            WebSocketCircuitBreaker circuitBreaker) {
        if (redisTemplate == null) {
            log.warn("[WebSocket] StringRedisTemplate 不存在，OfflineMessageStore 降级为 no-op");
            return new OfflineMessageStore() {
                @Override
                public void cacheOffline(String userId, String type, Object payload) { }
                @Override
                public List<String> drainOffline(String userId) { return List.of(); }
                @Override
                public long countOffline(String userId) { return 0; }
            };
        }
        log.info("[WebSocket] 注册 RedisOfflineMessageStore");
        return new RedisOfflineMessageStore(redisTemplate, properties, circuitBreaker);
    }

    // ==================== P0-3: 心跳保活 ====================

    @Bean
    @ConditionalOnMissingBean(WebSocketHeartbeatHandler.class)
    public WebSocketHeartbeatHandler webSocketHeartbeatHandler(
            OnlineUserService onlineUserService,
            WebSocketProperties properties) {
        log.info("[WebSocket] 注册 WebSocketHeartbeatHandler (staleTimeout={}ms)",
                properties.getHeartbeat().getStaleSessionTimeout());
        return new WebSocketHeartbeatHandler(properties, onlineUserService);
    }

    // ==================== P1-2: ACK 确认 ====================

    @Bean
    @ConditionalOnMissingBean(MessageAckService.class)
    @ConditionalOnProperty(prefix = "ydsz.websocket.ack", name = "enabled", havingValue = "true")
    public MessageAckService messageAckService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            WebSocketProperties properties) {
        log.info("[WebSocket] 注册 MessageAckService (timeout={})", properties.getAck().getTimeout());
        return new MessageAckService(redisTemplate, properties);
    }

    // ==================== P0-4: 重试队列 + 死信队列 ====================

    @Bean
    @ConditionalOnMissingBean(DeadLetterQueue.class)
    @ConditionalOnProperty(prefix = "ydsz.websocket.retry", name = "dead-letter-enabled", havingValue = "true")
    public DeadLetterQueue deadLetterQueue(
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        log.info("[WebSocket] 注册 RedisDeadLetterQueue");
        return new RedisDeadLetterQueue(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(MessageRetryQueue.class)
    @ConditionalOnProperty(prefix = "ydsz.websocket.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MessageRetryQueue messageRetryQueue(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            WebSocketProperties properties,
            @Autowired(required = false) DeadLetterQueue deadLetterQueue) {
        if (redisTemplate == null) {
            log.warn("[WebSocket] StringRedisTemplate 不存在，MessageRetryQueue 降级为 no-op");
            return new MessageRetryQueue() {
                @Override
                public void enqueue(RetryableMessage message) { }
                @Override
                public List<RetryableMessage> dequeueExpired(int maxCount) { return List.of(); }
                @Override
                public void markSuccess(String messageId) { }
                @Override
                public void markFailed(String messageId) { }
                @Override
                public long getPendingCount() { return 0; }
            };
        }
        log.info("[WebSocket] 注册 RedisMessageRetryQueue (maxRetries={})", properties.getRetry().getMaxRetries());
        return new RedisMessageRetryQueue(redisTemplate, properties, deadLetterQueue);
    }

    // ==================== 会话事件监听器 ====================

    @Bean
    @ConditionalOnMissingBean(WebSocketSessionEventListener.class)
    public WebSocketSessionEventListener webSocketSessionEventListener(
            OnlineUserService onlineUserService,
            OfflineMessageStore offlineMessageStore,
            SimpMessagingTemplate messagingTemplate,
            @Autowired(required = false) WebSocketHeartbeatHandler heartbeatHandler,
            @Autowired(required = false) WebSocketAuditService auditService,
            @Autowired(required = false) List<WebSocketConnectionListener> connectionListeners,
            @Autowired(required = false) SlowConnectionDetector slowConnectionDetector) {
        log.info("[WebSocket] 注册 WebSocketSessionEventListener");
        return new WebSocketSessionEventListener(
                onlineUserService, offlineMessageStore, messagingTemplate,
                heartbeatHandler, auditService, slowConnectionDetector, connectionListeners);
    }

    // ==================== 指标收集器 ====================

    @Bean
    @ConditionalOnMissingBean(WebSocketMetrics.class)
    public WebSocketMetrics webSocketMetrics(@Autowired(required = false) MeterRegistry meterRegistry) {
        log.info("[WebSocket] 注册 WebSocketMetrics");
        return new WebSocketMetrics(meterRegistry);
    }

    // ==================== 速率限制器 ====================

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(WebSocketRateLimiter.class)
    public WebSocketRateLimiter webSocketRateLimiter(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            WebSocketProperties properties,
            WebSocketCircuitBreaker circuitBreaker) {
        log.info("[WebSocket] 注册 WebSocketRateLimiter (enabled={})", properties.getRateLimit().isEnabled());
        return new WebSocketRateLimiter(redisTemplate, properties, circuitBreaker);
    }

    // ==================== P3-1: STOMP 消息拦截器 ====================

    @Bean
    @ConditionalOnMissingBean(StompMessageInterceptor.class)
    public StompMessageInterceptor stompMessageInterceptor(
            @Autowired(required = false) WebSocketRateLimiter rateLimiter,
            @Autowired(required = false) WebSocketAuditService auditService) {
        log.info("[WebSocket] 注册 StompMessageInterceptor");
        return new StompMessageInterceptor(rateLimiter, auditService);
    }

    // ==================== P0-1: 健康检查 ====================

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(name = "webSocketHealthIndicator")
    public HealthIndicator webSocketHealthIndicator(
            WebSocketProperties properties,
            WebSocketSessionEventListener eventListener,
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        log.info("[WebSocket] 注册 WebSocketHealthIndicator");
        return new WebSocketHealthIndicator(properties,
                eventListener.getActiveConnectionsCounter(), redisTemplate);
    }

    // ==================== 统一推送模板 ====================

    @Bean
    @ConditionalOnMissingBean(RealtimePushTemplate.class)
    public RealtimePushTemplate realtimePushTemplate(
            SimpMessagingTemplate messagingTemplate,
            @Autowired(required = false) WebSocketClusterPublisher clusterPublisher,
            OnlineUserService onlineUserService,
            OfflineMessageStore offlineMessageStore,
            WebSocketMetrics webSocketMetrics,
            MessageSerializer messageSerializer,
            MessageCompressor messageCompressor,
            @Autowired(required = false) WebSocketAuditService auditService,
            @Autowired(required = false) SlowConnectionDetector slowConnectionDetector,
            @Autowired(required = false) MessageAckService ackService,
            @Autowired(required = false) MessageRetryQueue retryQueue,
            @Autowired(required = false) List<MessageFilter> messageFilters) {
        log.info("[WebSocket] 注册 DefaultRealtimePushTemplate");
        return new DefaultRealtimePushTemplate(
                messagingTemplate,
                clusterPublisher != null ? clusterPublisher : new NoOpClusterPublisher(),
                onlineUserService, offlineMessageStore, webSocketMetrics,
                messageSerializer, messageCompressor, auditService,
                slowConnectionDetector, ackService, retryQueue,
                messageFilters);
    }

    @Bean
    public RetryFlushTask retryFlushTask(
            RealtimePushTemplate pushTemplate,
            @Autowired(required = false) MessageAckService ackService) {
        return new RetryFlushTask(pushTemplate, ackService);
}

    public static class RetryFlushTask {
        private final RealtimePushTemplate pushTemplate;
        private final MessageAckService ackService;

        public RetryFlushTask(RealtimePushTemplate pushTemplate, MessageAckService ackService) {
            this.pushTemplate = pushTemplate;
            this.ackService = ackService;
}

        @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 10000)
        public void flush() {
            pushTemplate.flushRetryMessages();
            if (ackService != null) {
                ackService.cleanupExpiredLocalAcks();
}
}
}
    /**
     * No-op 集群发布者（集群未启用时的降级实现，始终返回 false 触发本地推送）。
     */
    private static class NoOpClusterPublisher extends WebSocketClusterPublisher {
        NoOpClusterPublisher() {
            super(null, null, null);
        }

        @Override
        public boolean publish(WebSocketClusterMessage message) {
            return false;
        }
    }
}
