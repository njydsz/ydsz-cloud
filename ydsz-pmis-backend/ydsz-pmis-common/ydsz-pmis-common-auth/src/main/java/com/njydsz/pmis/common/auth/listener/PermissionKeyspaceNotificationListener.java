package com.njydsz.pmis.common.auth.listener;

import com.njydsz.pmis.common.auth.service.RbacPermissionEvaluator;
import com.njydsz.pmis.common.util.string.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Redis Keyspace Notification 的权限缓存失效监听器。
 *
 * <p>监听 Redis 中权限相关 key 的变更事件（set/del/expired），
 * 精确触发本地缓存失效，替代 Pub/Sub 广播模式的不保证送达问题。
 *
 * <p><b>工作原理：</b>
 * <ol>
 *   <li>Redis 开启 notify-keyspace-events 配置（至少包含 KEA）</li>
 *   <li>监听 __keyevent@0__:set、__keyevent@0__:del、__keyevent@0__:expired 频道</li>
 *   <li>当 remi-auth:role-* 相关的 key 发生变更时，解析 roleCode 并清理缓存</li>
 * </ol>
 *
 * <p><b>配置要求：</b>
 * Redis 需要开启 keyspace notification，配置: notify-keyspace-events KEA
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class PermissionKeyspaceNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionKeyspaceNotificationListener.class);

    /**
     * 匹配 role 相关 key 的正则，提取 roleCode。
     * Key 格式示例：remi-auth:role-menu:admin, remi-auth:role-api:admin
     */
    private static final Pattern ROLE_KEY_PATTERN = Pattern.compile(
            "remi-auth:role-(?:menu|api|row|col):([^:]+)");

    private final RbacPermissionEvaluator permissionEvaluator;

    public PermissionKeyspaceNotificationListener(RbacPermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * 注册 Keyspace Notification 订阅。
     *
     * @param container Redis 消息监听容器
     */
    public void registerKeyspaceSubscription(RedisMessageListenerContainer container) {
        KeyspacePermissionMessageListener messageListener = new KeyspacePermissionMessageListener();
        // 订阅 set、del、expired 事件
        String[] events = {"set", "del", "expired"};
        for (String event : events) {
            ChannelTopic topic = new ChannelTopic("__keyevent@0__:" + event);
            container.addMessageListener(messageListener, topic);
        }
        log.info("Redis Keyspace Notification 权限缓存失效监听器已注册, events=[set, del, expired]");
    }

    /**
     * Redis Keyspace 事件消息监听器。
     *
     * <p>监听 keyevent 频道，message.getBody() 是发生变更的 key 名称。
     */
    private class KeyspacePermissionMessageListener implements MessageListener {

        @Override
        public void onMessage(@org.springframework.lang.NonNull Message message, @org.springframework.lang.Nullable byte[] pattern) {
            try {
                String channel = new String(message.getChannel());
                String key = new String(message.getBody());

                if (log.isDebugEnabled()) {
                    log.debug("收到 Redis Keyspace 事件: channel={}, key={}", channel, key);
                }

                // 匹配 role 相关 key
                Matcher matcher = ROLE_KEY_PATTERN.matcher(key);
                if (matcher.find()) {
                    String roleCode = matcher.group(1);
                    if (StringUtils.isNotBlank(roleCode)) {
                        log.info("Redis Keyspace 事件触发权限缓存失效: key={}, roleCode={}", key, roleCode);
                        permissionEvaluator.clearCachesByRoleCodes(roleCode);
                    }
                }
            } catch (Exception e) {
                log.error("处理 Redis Keyspace 事件异常: {}", e.getMessage(), e);
            }
        }
    }
}
