package com.njydsz.common.auth.event;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import com.njydsz.common.auth.service.ColumnPermissionResolver;
import com.njydsz.common.auth.service.DataPermissionResolver;
import com.njydsz.common.auth.service.RolePermissionLoader;
import com.njydsz.common.auth.service.impl.RedisRoleColumnPermissionResolver;
import com.njydsz.common.auth.service.impl.RedisRoleDataPermissionResolver;
import com.njydsz.common.auth.service.impl.RedisRolePermissionLoader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 权限变更事件监听器。
 *
 * <p>监听两类权限变更通知：
 * <ol>
 *   <li>Spring ApplicationEvent：来自本地节点的权限变更事件</li>
 *   <li>Redis Pub/Sub：来自其他集群节点的权限变更通知</li>
 * </ol>
 *
 * <p>收到通知后清除对应角色的本地缓存，确保权限变更即时生效。
 *
 * <p><b>缓存失效策略：</b>
 * <ul>
 *   <li>ROLE_PERMISSION_CHANGED：清除 RolePermissionLoader 中的缓存</li>
 *   <li>ROLE_DATA_SCOPE_CHANGED：清除 DataPermissionResolver 中的缓存</li>
 *   <li>ROLE_COLUMN_PERMISSION_CHANGED：清除 ColumnPermissionResolver 中的缓存</li>
 *   <li>ROLE_DELETED：清除以上所有相关缓存</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see PermissionChangedEvent
 * @see PermissionChangePublisher
 */
@Slf4j
@RequiredArgsConstructor
public class PermissionChangeCacheInvalidator {

    private static final String PERMISSION_CHANGE_CHANNEL = "ydsz-auth:permission:changed";

    private final RolePermissionLoader rolePermissionLoader;
    private final DataPermissionResolver dataPermissionResolver;
    private final ColumnPermissionResolver columnPermissionResolver;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    /**
     * Bean 初始化时订阅 Redis 权限变更频道。
     *
     * <p>在容器启动阶段向 {@link RedisMessageListenerContainer} 注册监听器，
     * 以接收其他集群节点通过 Pub/Sub 下发的权限变更通知。订阅失败仅记录错误日志，
     * 不影响本地 Spring {@code ApplicationEvent} 通道的缓存失效能力。</p>
     */
    @PostConstruct
    public void init() {
        subscribeToRedisChannel();
    }

    private void subscribeToRedisChannel() {
        try {
            MessageListenerAdapter adapter = new MessageListenerAdapter(new RedisMessageListener());
            adapter.setSerializer(null);
            redisMessageListenerContainer.addMessageListener(adapter, new ChannelTopic(PERMISSION_CHANGE_CHANNEL));
            log.info("权限变更监听器已订阅 Redis 频道：{}", PERMISSION_CHANGE_CHANNEL);
        } catch (Exception e) {
            log.error("权限变更监听器订阅 Redis 频道失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 处理本地节点发出的权限变更事件。
     *
     * <p>作为 Spring {@code @EventListener} 监听 {@link PermissionChangedEvent}；
     * 事件为 {@code null} 时直接忽略。最终委派 {@link #handlePermissionChange} 按变更类型失效对应角色的各权限缓存。</p>
     *
     * @param event 权限变更事件，允许为 {@code null}（忽略不处理）
     */
    @EventListener
    public void onPermissionChanged(PermissionChangedEvent event) {
        if (event == null) {
            return;
        }
        log.info("收到权限变更事件（本地）：{}", event);
        handlePermissionChange(event);
    }

    private void handlePermissionChange(PermissionChangedEvent event) {
        String roleCode = event.getRoleCode();
        if (roleCode == null || roleCode.trim().isEmpty()) {
            log.warn("权限变更处理失败：roleCode 为空");
            return;
        }

        PermissionChangedEvent.PermissionChangeType changeType = event.getChangeType();
        if (changeType == null) {
            log.warn("权限变更处理失败：changeType 为空");
            return;
        }

        switch (changeType) {
            case ROLE_PERMISSION_CHANGED:
                invalidateRolePermissionCache(roleCode);
                break;
            case ROLE_DATA_SCOPE_CHANGED:
                invalidateDataPermissionCache(roleCode);
                break;
            case ROLE_COLUMN_PERMISSION_CHANGED:
                invalidateColumnPermissionCache(roleCode);
                break;
            case ROLE_DELETED:
                invalidateAllCaches(roleCode);
                break;
            default:
                log.warn("权限变更处理失败：未知变更类型 {}", changeType);
        }
    }

    private void invalidateRolePermissionCache(String roleCode) {
        try {
            if (rolePermissionLoader instanceof RedisRolePermissionLoader) {
                ((RedisRolePermissionLoader) rolePermissionLoader).invalidate(roleCode);
                log.info("角色权限缓存已失效：roleCode={}", roleCode);
            }
        } catch (Exception e) {
            log.error("角色权限缓存失效失败：roleCode={}, error={}", roleCode, e.getMessage(), e);
        }
    }

    private void invalidateDataPermissionCache(String roleCode) {
        try {
            if (dataPermissionResolver instanceof RedisRoleDataPermissionResolver) {
                ((RedisRoleDataPermissionResolver) dataPermissionResolver).invalidate(roleCode);
                log.info("数据权限缓存已失效：roleCode={}", roleCode);
            }
        } catch (Exception e) {
            log.error("数据权限缓存失效失败：roleCode={}, error={}", roleCode, e.getMessage(), e);
        }
    }

    private void invalidateColumnPermissionCache(String roleCode) {
        try {
            if (columnPermissionResolver instanceof RedisRoleColumnPermissionResolver) {
                ((RedisRoleColumnPermissionResolver) columnPermissionResolver).invalidate(roleCode);
                log.info("列权限缓存已失效：roleCode={}", roleCode);
            }
        } catch (Exception e) {
            log.error("列权限缓存失效失败：roleCode={}, error={}", roleCode, e.getMessage(), e);
        }
    }

    private void invalidateAllCaches(String roleCode) {
        invalidateRolePermissionCache(roleCode);
        invalidateDataPermissionCache(roleCode);
        invalidateColumnPermissionCache(roleCode);
        log.info("角色所有权限缓存已失效：roleCode={}", roleCode);
    }

    /**
     * Redis Pub/Sub 权限变更消息监听器。
     *
     * <p>订阅 {@value #PERMISSION_CHANGE_CHANNEL} 频道，接收其他集群节点下发的权限变更通知；
     * 消息格式为 {@code roleCode|changeType|affectedTypes|...|sourceNode}，解析失败或格式非法时
     * 记录告警日志并忽略，不影响本地缓存状态。
     */
    private class RedisMessageListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                String body = new String(message.getBody());
                String channel = new String(message.getChannel());
                log.debug("收到 Redis Pub/Sub 消息：channel={}, body={}", channel, body);

                PermissionChangedEvent event = parseMessage(body);
                if (event != null) {
                    log.info("收到权限变更事件（Redis）：{}", event);
                    handlePermissionChange(event);
                }
            } catch (Exception e) {
                log.error("处理 Redis Pub/Sub 消息失败：{}", e.getMessage(), e);
            }
        }

        private PermissionChangedEvent parseMessage(String message) {
            if (message == null || message.trim().isEmpty()) {
                return null;
            }
            String[] parts = message.split("\\|", -1);
            if (parts.length < 3) {
                log.warn("权限变更消息格式错误：{}", message);
                return null;
            }
            try {
                String roleCode = parts[0].trim();
                PermissionChangedEvent.PermissionChangeType changeType = PermissionChangedEvent.PermissionChangeType.valueOf(parts[1].trim());
                String affectedTypesStr = parts[2].trim();
                Set<String> affectedTypes = affectedTypesStr.isEmpty() ? null
                        : Arrays.stream(affectedTypesStr.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toSet());
                String sourceNode = parts.length > 4 ? parts[4].trim() : null;

                return new PermissionChangedEvent(roleCode, changeType, affectedTypes, sourceNode);
            } catch (Exception e) {
                log.error("权限变更消息解析失败：{}, error={}", message, e.getMessage());
                return null;
            }
        }
    }
}
