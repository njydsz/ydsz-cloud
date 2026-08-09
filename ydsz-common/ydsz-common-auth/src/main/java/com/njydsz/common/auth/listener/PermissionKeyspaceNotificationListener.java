package com.njydsz.common.auth.listener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.njydsz.common.auth.service.RbacPermissionEvaluator;
import com.njydsz.common.util.string.StringUtils;

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
 *   <li>当 ydsz-auth:role-* 相关的 key 发生变更时，解析 roleCode 并清理缓存</li>
 * </ol>
 *
 * <p><b>配置要求：</b>
 * Redis 需要开启 keyspace notification，配置: notify-keyspace-events KEA
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class PermissionKeyspaceNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionKeyspaceNotificationListener.class);

    /**
     * 匹配 role 相关 key 的正则，提取 roleCode。
     * 支持自定义 key 前缀模式，默认匹配 ydsz-auth:role-* 格式。
     */
    private final Pattern roleKeyPattern;

    private final RbacPermissionEvaluator permissionEvaluator;

    public PermissionKeyspaceNotificationListener(RbacPermissionEvaluator permissionEvaluator) {
        this(permissionEvaluator, "ydsz-auth:role");
    }

    public PermissionKeyspaceNotificationListener(RbacPermissionEvaluator permissionEvaluator, String keyPrefix) {
        this.permissionEvaluator = permissionEvaluator;
        String prefix = (keyPrefix != null && !keyPrefix.isEmpty()) ? keyPrefix : "ydsz-auth:role";
        // 动态构建正则：匹配 prefix-(?:menu|api|row|col):roleCode
        // 转义 prefix 中的特殊字符
        String escapedPrefix = Pattern.quote(prefix);
        this.roleKeyPattern = Pattern.compile(
                escapedPrefix + "-(?:menu|api|row|col):([^:]+)");
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
        public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
            try {
                String channel = new String(message.getChannel());
                String key = new String(message.getBody());

                if (log.isDebugEnabled()) {
                    log.debug("收到 Redis Keyspace 事件: channel={}, key={}", channel, key);
                }

                // 匹配 role 相关 key
                Matcher matcher = roleKeyPattern.matcher(key);
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
