package com.njydsz.pmis.common.socket.config;

import com.njydsz.pmis.common.auth.token.TokenService;
import com.njydsz.pmis.common.socket.cluster.WebSocketClusterMessage;
import com.njydsz.pmis.common.socket.cluster.WebSocketClusterPublisher;
import com.njydsz.pmis.common.socket.auth.WebSocketAuthInterceptor;
import com.njydsz.pmis.common.socket.metric.WebSocketMetrics;
import com.njydsz.pmis.common.socket.offline.OfflineMessageStore;
import com.njydsz.pmis.common.socket.offline.RedisOfflineMessageStore;
import com.njydsz.pmis.common.socket.push.DefaultRealtimePushTemplate;
import com.njydsz.pmis.common.socket.push.RealtimePushTemplate;
import com.njydsz.pmis.common.socket.ratelimit.WebSocketRateLimiter;
import com.njydsz.pmis.common.socket.session.OnlineUserService;
import com.njydsz.pmis.common.socket.session.WebSocketSessionEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.List;
/**
 * WebSocket 自动装配配置。
 *
 * <p>当 classpath 存在 {@link SimpMessagingTemplate} 且 {@code pmis.websocket.enabled=true} 时自动生效。
 *
 * <p>自动注册以下 Bean：
 * <ul>
 *   <li>{@link WebSocketAuthInterceptor} — JWT 握手鉴权（当 TokenService 在 classpath 时）</li>
 *   <li>{@link OnlineUserService} — 在线状态服务（当 StringRedisTemplate 在 classpath 时）</li>
 *   <li>{@link OfflineMessageStore} — 离线消息存储（Redis 默认实现）</li>
 *   <li>{@link WebSocketSessionEventListener} — 会话事件监听器</li>
 *   <li>{@link RealtimePushTemplate} — 统一推送模板</li>
 *   <li>{@link WebSocketMetrics} — Micrometer 指标</li>
 *   <li>{@link WebSocketRateLimiter} — 速率限制器</li>
 *   <li>STOMP 端点配置 — 注册 WebSocket 端点 + 鉴权拦截器</li>
 * </ul>
 *
 * <p>业务方可通过覆盖 Bean 定义自定义行为，例如：
 * <pre>{@code
 * @Bean
 * public OfflineMessageStore offlineMessageStore() {
 *     return new DbOfflineMessageStore(); // 使用数据库持久化
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(WebSocketProperties.class)
@ConditionalOnClass(SimpMessagingTemplate.class)
@ConditionalOnProperty(prefix = "pmis.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketAutoConfiguration {

    /**
     * JWT 握手鉴权拦截器（当 TokenService 在 classpath 时自动注册）。
     *
     * @param tokenService Token 服务
     * @return WebSocket 鉴权拦截器
     */
    @Bean
    @ConditionalOnClass(TokenService.class)
    @ConditionalOnMissingBean(WebSocketAuthInterceptor.class)
    public WebSocketAuthInterceptor webSocketAuthInterceptor(TokenService tokenService) {
        log.info("[WebSocket] 注册 JWT 握手鉴权拦截器");
        return new WebSocketAuthInterceptor(tokenService);
    }

    /**
     * 在线用户状态服务（当 StringRedisTemplate 在 classpath 时自动注册）。
     *
     * @param redisTemplate Redis 模板
     * @param properties    WebSocket 配置
     * @return 在线状态服务
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(OnlineUserService.class)
    @ConditionalOnProperty(prefix = "pmis.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
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

    /**
     * 离线消息存储（Redis 默认实现）。
     *
     * @param redisTemplate Redis 模板
     * @param properties    WebSocket 配置
     * @return 离线消息存储
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(OfflineMessageStore.class)
    @ConditionalOnProperty(prefix = "pmis.websocket.offline", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OfflineMessageStore offlineMessageStore(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            WebSocketProperties properties) {
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
        return new RedisOfflineMessageStore(redisTemplate, properties);
    }

    /**
     * WebSocket 会话事件监听器。
     *
     * @param onlineUserService  在线状态服务
     * @param offlineMessageStore 离线消息存储
     * @param messagingTemplate  STOMP 消息模板
     * @return 会话事件监听器
     */
    @Bean
    @ConditionalOnMissingBean(WebSocketSessionEventListener.class)
    public WebSocketSessionEventListener webSocketSessionEventListener(
            OnlineUserService onlineUserService,
            OfflineMessageStore offlineMessageStore,
            SimpMessagingTemplate messagingTemplate) {
        log.info("[WebSocket] 注册 WebSocketSessionEventListener");
        return new WebSocketSessionEventListener(onlineUserService, offlineMessageStore, messagingTemplate);
    }

    /**
     * WebSocket 指标收集器（当 MeterRegistry 在 classpath 时自动注册）。
     *
     * @param meterRegistry MeterRegistry（可为 null）
     * @return WebSocket 指标收集器
     */
    @Bean
    @ConditionalOnMissingBean(WebSocketMetrics.class)
    public WebSocketMetrics webSocketMetrics(@Autowired(required = false) MeterRegistry meterRegistry) {
        log.info("[WebSocket] 注册 WebSocketMetrics");
        return new WebSocketMetrics(meterRegistry);
    }

    /**
     * WebSocket 速率限制器（当 StringRedisTemplate 在 classpath 时自动注册）。
     *
     * @param redisTemplate Redis 模板
     * @param properties    WebSocket 配置
     * @return 速率限制器
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(WebSocketRateLimiter.class)
    public WebSocketRateLimiter webSocketRateLimiter(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            WebSocketProperties properties) {
        log.info("[WebSocket] 注册 WebSocketRateLimiter (enabled={})", properties.getRateLimit().isEnabled());
        return new WebSocketRateLimiter(redisTemplate, properties);
    }

    /**
     * 统一实时推送模板。
     *
     * @param messagingTemplate  STOMP 消息模板
     * @param clusterPublisher   集群广播发布者（可为 null，降级为纯本地推送）
     * @param onlineUserService  在线状态服务
     * @param offlineMessageStore 离线消息存储
     * @param webSocketMetrics   指标收集器
     * @return 默认推送模板实现
     */
    @Bean
    @ConditionalOnMissingBean(RealtimePushTemplate.class)
    public RealtimePushTemplate realtimePushTemplate(
            SimpMessagingTemplate messagingTemplate,
            @Autowired(required = false) WebSocketClusterPublisher clusterPublisher,
            OnlineUserService onlineUserService,
            OfflineMessageStore offlineMessageStore,
            WebSocketMetrics webSocketMetrics) {
        log.info("[WebSocket] 注册 DefaultRealtimePushTemplate");
        return new DefaultRealtimePushTemplate(
                messagingTemplate,
                clusterPublisher != null ? clusterPublisher : new NoOpClusterPublisher(),
                onlineUserService,
                offlineMessageStore,
                webSocketMetrics);
    }

    /**
     * No-op 集群发布者（集群未启用时的降级实现，始终返回 false 触发本地推送）。
     */
    private static class NoOpClusterPublisher extends com.njydsz.pmis.common.socket.cluster.WebSocketClusterPublisher {
        NoOpClusterPublisher() {
            super(null, null);
        }

        @Override
        public boolean publish(WebSocketClusterMessage message) {
            return false;
        }
    }
}
