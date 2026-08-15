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

import org.springframework.scheduling.annotation.Scheduled;
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

    /**
     * 创建慢连接检测器 Bean。
     *
     * @param properties    WebSocket 配置属性
     * @param webSocketMetrics 指标收集器
     * @return 慢连接检测器实例
     */
    @Bean
    @ConditionalOnMissingBean(SlowConnectionDetector.class)
    public SlowConnectionDetector slowConnectionDetector(
            WebSocketProperties properties, WebSocketMetrics metrics) {
        log.info("[WebSocket] 注册 SlowConnectionDetector (enabled={})", properties.getSlowConnection().isEnabled());
        return new SlowConnectionDetector(properties, metrics);
    }

    // ==================== P2-1: 连接数限制 ====================

    /**
     * 注册全局/单用户连接数限制器 Bean。
     *
     * <p>基于在线用户服务计数与配置上限，在握手阶段拦截超额连接，防止单用户或整体连接数打爆导致 OOM/雪崩。
     * 无自定义 Bean 时注册默认实现。
     */
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

    /**
     * 注册 WebSocket 握手鉴权拦截器 Bean。
     *
     * <p>在 STOMP 握手阶段校验 JWT、结合连接数限制器做准入控制，并联动审计服务记录连接事件。
     * 依赖 TokenService 类存在时启用；无自定义 Bean 时注册默认实现。
     */
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

    /**
     * 注册在线用户状态服务 Bean。
     *
     * <p>维护用户-会话的在线映射与会话 TTL，支撑单点/多点登录与离线判定。
     * Redis 为可选依赖，缺失时降级为 no-op 实现（仅记录日志、不实际维护状态），避免阻塞主流程。
     */
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

    /**
     * 注册离线消息存储 Bean。
     *
     * <p>缓存接收方离线期间的下发消息，待其上线后拉取补投。Redis 存在时落地 {@link RedisOfflineMessageStore}，
     * 缺失时降级为 no-op；并叠加熔断器保护，避免 Redis 故障拖垮推送链路。
     */
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

    /**
     * 注册心跳保活处理器 Bean。
     *
     * <p>定期探测并清理超时空闲会话，回收服务端资源、及时感知断线。
     * 依据配置 staleSessionTimeout 判定过期；无自定义 Bean 时注册默认实现。
     */
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

    /**
     * 注册消息 ACK 确认服务 Bean。
     *
     * <p>追踪每条推送消息的客户端确认回执，未确认消息可被重试或转入离线存储，支撑"至少一次"投递语义。
     * 仅在 ack 启用时创建；Redis 可选，缺失时仅内存维护。
     */
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

    /**
     * 注册死信队列 Bean。
     *
     * <p>承接重试后仍不可达的消息，避免其无限占用重试队列；默认 Redis 实现，按死信开关启用。
     * 无自定义 Bean 时注册，业务可替换为持久化实现。
     */
    @Bean
    @ConditionalOnMissingBean(DeadLetterQueue.class)
    @ConditionalOnProperty(prefix = "ydsz.websocket.retry", name = "dead-letter-enabled", havingValue = "true")
    public DeadLetterQueue deadLetterQueue(
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        log.info("[WebSocket] 注册 RedisDeadLetterQueue");
        return new RedisDeadLetterQueue(redisTemplate);
    }

    /**
     * 注册消息重试队列 Bean。
     *
     * <p>暂存未确认/发送失败的消息并按延迟重新投递；Redis 存在时落地，缺失时降级为 no-op，
     * 达到最大重试次数后转交死信队列。保证推送在短暂故障后最终可达。
     */
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

    /**
     * 注册会话事件监听器 Bean。
     *
     * <p>在连接/断开通告中联动在线状态维护、离线消息补投、心跳、审计与各业务监听器，
     * 是会话生命周期的编排中枢；无自定义 Bean 时注册默认实现。
     */
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

    /**
     * 注册 WebSocket 指标收集器 Bean。
     *
     * <p>聚合连接数、消息吞吐、延迟等 Metrics 接入 Micrometer（可选），为容量评估与告警提供底座；
     * 无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(WebSocketMetrics.class)
    public WebSocketMetrics webSocketMetrics(@Autowired(required = false) MeterRegistry meterRegistry) {
        log.info("[WebSocket] 注册 WebSocketMetrics");
        return new WebSocketMetrics(meterRegistry);
    }

    // ==================== 速率限制器 ====================

    /**
     * 注册 WebSocket 速率限制器 Bean。
     *
     * <p>按连接/用户维度限流消息发送频率，防止单客户端刷屏或恶意压测打爆服务端；
     * Redis 可选，缺失时退化为仅内存限流。无自定义 Bean 时注册默认实现。
     */
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

    /**
     * 注册 STOMP 消息拦截器 Bean。
     *
     * <p>在消息收发链路做统一的限流与审计前置校验，是横切关注点（安全/计量）的接入点；
     * 无自定义 Bean 时注册默认实现。
     */
    @Bean
    @ConditionalOnMissingBean(StompMessageInterceptor.class)
    public StompMessageInterceptor stompMessageInterceptor(
            @Autowired(required = false) WebSocketRateLimiter rateLimiter,
            @Autowired(required = false) WebSocketAuditService auditService) {
        log.info("[WebSocket] 注册 StompMessageInterceptor");
        return new StompMessageInterceptor(rateLimiter, auditService);
    }

    // ==================== P0-1: 健康检查 ====================

    /**
     * 注册 WebSocket 健康检查指示器 Bean。
     *
     * <p>暴露活跃连接数、Redis 可用性等健康度到 Actuator /health，供探活与告警；
     * 依赖 Spring HealthIndicator 类存在时启用。无自定义 Bean 时注册默认实现。
     */
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

    /**
     * 注册统一实时推送模板 Bean。
     *
     * <p>封装点对点/广播/集群推送的完整链路（在线下发、离线存储、压缩、序列化、ACK、重试、审计、过滤），
     * 业务侧仅需调用模板即可完成推送；集群未启用时以 NoOp 发布者降级为本地推送。无自定义 Bean 时注册默认实现。
     */
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

    /**
     * 注册重试刷新定时任务 Bean。
     *
     * <p>周期性触发推送模板的重试消息重投与 ACK 过期本地回执清理，是异步投递补偿的执行入口；
     * 由 Spring 托管生命周期，无需手动启停。
     */
    @Bean
    public RetryFlushTask retryFlushTask(
            RealtimePushTemplate pushTemplate,
            @Autowired(required = false) MessageAckService ackService) {
        return new RetryFlushTask(pushTemplate, ackService);
}

    /**
     * 重试刷新定时任务。
     *
     * <p>由 Spring 调度线程周期性驱动：重投推送模板中待重试的消息，并清理
     * ACK 服务的本地过期回执。作为异步投递的补偿执行入口，保证"至少一次"投递语义
     * 下的延迟消息最终可达。
     */
    public static class RetryFlushTask {
        private final RealtimePushTemplate pushTemplate;
        private final MessageAckService ackService;

        public RetryFlushTask(RealtimePushTemplate pushTemplate, MessageAckService ackService) {
            this.pushTemplate = pushTemplate;
            this.ackService = ackService;
}

        /**
         * 定时重投重试消息并清理过期本地 ACK。
         *
         * <p>每 10 秒执行一次（{@code fixedDelay=10000}），驱动 {@link RealtimePushTemplate#flushRetryMessages()}
         * 与 ACK 服务的本地过期回执回收，保障延迟消息最终可达。
         */
        @Scheduled(fixedDelay = 10000)
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
