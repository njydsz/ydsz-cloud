package com.njydsz.common.auth.event;

import com.njydsz.common.json.tree.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.njydsz.common.auth.service.RbacPermissionEvaluator;
import com.njydsz.common.json.YdszJson;

/**
 * 权限变更事件监听器
 *
 * <p>监听 {@link PermissionChangedEvent}，在权限变更时自动使缓存失效。
 *
 * <p><b>多实例同步：</b>
 * 当 ydsz-common-redis 可用时，通过 Redis Pub/Sub 将缓存失效消息广播到其他实例，
 * 确保多实例部署场景下各节点缓存一致性。
 *
 * <p><b>触发场景：</b>
 * <ul>
 *   <li>业务代码发布权限变更事件后自动清理对应角色的权限缓存</li>
 *   <li>角色权限被修改后自动清理缓存</li>
 *   <li>用户角色被分配后自动清理缓存</li>
 * </ul>
 *
 * @since 1.0.0
 * 
 */
public class PermissionCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheInvalidationListener.class);

    /**
     * Redis Pub/Sub 频道名称
     */
    public static final String CACHE_INVALIDATION_CHANNEL = "ydsz-auth:permission:changed";

    private final RbacPermissionEvaluator permissionEvaluator;

    public PermissionCacheInvalidationListener(RbacPermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * 监听权限变更事件并清理对应缓存（同步监听）
     *
     * @param event 权限变更事件
     */
    @EventListener
    public void onPermissionChanged(PermissionChangedEvent event) {
        log.info("接收到权限变更事件, type={}, roleCode={}", event.getChangeType(), event.getRoleCode());

        handleInvalidation(event.getChangeType(), event.getRoleCode());
    }

    /**
     * 处理缓存失效逻辑
     */
    private void handleInvalidation(PermissionChangedEvent.PermissionChangeType changeType, String roleCode) {
        switch (changeType) {
            case ROLE_PERMISSION_CHANGED:
            case USER_ROLE_CHANGED:
                if (roleCode != null && !"ALL".equals(roleCode)) {
                    permissionEvaluator.clearCachesByRoleCodes(roleCode);
                    log.info("已清理角色权限缓存: {}", roleCode);
                }
                break;

            case MENU_CHANGED:
            case COLUMN_PERMISSION_CHANGED:
            case ROLE_DATA_SCOPE_CHANGED:
            case ROLE_COLUMN_PERMISSION_CHANGED:
            case ROLE_DELETED:
            case ALL:
                permissionEvaluator.clearAllCaches();
                log.info("已清理全部权限缓存");
                break;

            default:
                log.warn("未知的权限变更类型: {}", changeType);
        }
    }

    /**
     * 创建 Redis Pub/Sub 订阅容器，监听缓存失效消息
     *
     * <p>当收到其他实例发布的缓存失效消息时，清除本地缓存。
     * 使用 {@code @ConditionalOnBean(StringRedisTemplate.class)} 条件化注册。
     *
     * @param redisTemplate Redis 模板
     * @param evaluator     权限评估器
     * @return RedisMessageListenerContainer 实例
     */
    public static RedisMessageListenerContainer createRedisSubscriber(
            StringRedisTemplate redisTemplate, RbacPermissionEvaluator evaluator) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisTemplate.getConnectionFactory());
        container.addMessageListener(new RedisCacheInvalidationMessageListener(evaluator),
                new ChannelTopic(CACHE_INVALIDATION_CHANNEL));
        container.afterPropertiesSet();
        container.start();
        log.info("权限缓存 Redis Pub/Sub 订阅已启动, channel={}", CACHE_INVALIDATION_CHANNEL);
        return container;
    }

    /**
     * Redis 缓存失效消息监听器
     *
     * <p>监听 Redis Pub/Sub 频道中的缓存失效消息，清除本地权限缓存。
     */
    private static class RedisCacheInvalidationMessageListener implements MessageListener {

        private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidationMessageListener.class);

        private final RbacPermissionEvaluator permissionEvaluator;

        RedisCacheInvalidationMessageListener(RbacPermissionEvaluator permissionEvaluator) {
            this.permissionEvaluator = permissionEvaluator;
        }

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                String body = new String(message.getBody());
                JsonNode json = YdszJson.readTree(body);
                String changeTypeName = json.has("changeType") ? json.get("changeType").asText(null) : null;
                String roleCode = json.has("roleCode") ? json.get("roleCode").asText(null) : null;

                if (changeTypeName == null) {
                    log.warn("收到无效的 Redis 缓存失效消息: {}", body);
                    return;
                }

                PermissionChangedEvent.PermissionChangeType changeType =
                        PermissionChangedEvent.PermissionChangeType.valueOf(changeTypeName);

                log.info("收到 Redis 缓存失效消息, changeType={}, roleCode={}", changeType, roleCode);

                switch (changeType) {
                    case ROLE_PERMISSION_CHANGED:
                    case USER_ROLE_CHANGED:
                        if (roleCode != null && !"ALL".equals(roleCode)) {
                            permissionEvaluator.clearCachesByRoleCodes(roleCode);
                            log.info("Redis 消息触发清理角色权限缓存: {}", roleCode);
                        }
                        break;

                    case MENU_CHANGED:
                    case COLUMN_PERMISSION_CHANGED:
                    case ROLE_DATA_SCOPE_CHANGED:
                    case ROLE_COLUMN_PERMISSION_CHANGED:
                    case ROLE_DELETED:
                    case ALL:
                        permissionEvaluator.clearAllCaches();
                        log.info("Redis 消息触发清理全部权限缓存");
                        break;

                    default:
                        log.warn("未知的 Redis 缓存失效类型: {}", changeType);
                }
            } catch (Exception e) {
                log.error("处理 Redis 缓存失效消息异常: {}", e.getMessage(), e);
            }
        }
    }
}
