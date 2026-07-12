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
 * 鍩轰簬 Redis Keyspace Notification 鐨勬潈闄愮紦瀛樺け鏁堢洃鍚櫒銆?
 *
 * <p>鐩戝惉 Redis 涓潈闄愮浉鍏?key 鐨勫彉鏇翠簨浠讹紙set/del/expired锛夛紝
 * 绮剧‘瑙﹀彂鏈湴缂撳瓨澶辨晥锛屾浛浠?Pub/Sub 骞挎挱妯″紡鐨勪笉淇濊瘉閫佽揪闂銆?
 *
 * <p><b>宸ヤ綔鍘熺悊锛?/b>
 * <ol>
 *   <li>Redis 寮€鍚?notify-keyspace-events 閰嶇疆锛堣嚦灏戝寘鍚?KEA锛?/li>
 *   <li>鐩戝惉 __keyevent@0__:set銆乢_keyevent@0__:del銆乢_keyevent@0__:expired 棰戦亾</li>
 *   <li>褰?remi-auth:role-* 鐩稿叧鐨?key 鍙戠敓鍙樻洿鏃讹紝瑙ｆ瀽 roleCode 骞舵竻鐞嗙紦瀛?/li>
 * </ol>
 *
 * <p><b>閰嶇疆瑕佹眰锛?/b>
 * Redis 闇€瑕佸紑鍚?keyspace notification锛岄厤缃? notify-keyspace-events KEA
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class PermissionKeyspaceNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionKeyspaceNotificationListener.class);

    /**
     * 鍖归厤 role 鐩稿叧 key 鐨勬鍒欙紝鎻愬彇 roleCode銆?
     * Key 鏍煎紡绀轰緥锛歳emi-auth:role-menu:admin, remi-auth:role-api:admin
     */
    private static final Pattern ROLE_KEY_PATTERN = Pattern.compile(
            "remi-auth:role-(?:menu|api|row|col):([^:]+)");

    private final RbacPermissionEvaluator permissionEvaluator;

    public PermissionKeyspaceNotificationListener(RbacPermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * 娉ㄥ唽 Keyspace Notification 璁㈤槄銆?
     *
     * @param container Redis 娑堟伅鐩戝惉瀹瑰櫒
     */
    public void registerKeyspaceSubscription(RedisMessageListenerContainer container) {
        KeyspacePermissionMessageListener messageListener = new KeyspacePermissionMessageListener();
        // 璁㈤槄 set銆乨el銆乪xpired 浜嬩欢
        String[] events = {"set", "del", "expired"};
        for (String event : events) {
            ChannelTopic topic = new ChannelTopic("__keyevent@0__:" + event);
            container.addMessageListener(messageListener, topic);
        }
        log.info("Redis Keyspace Notification 鏉冮檺缂撳瓨澶辨晥鐩戝惉鍣ㄥ凡娉ㄥ唽, events=[set, del, expired]");
    }

    /**
     * Redis Keyspace 浜嬩欢娑堟伅鐩戝惉鍣ㄣ€?
     *
     * <p>鐩戝惉 keyevent 棰戦亾锛宮essage.getBody() 鏄彂鐢熷彉鏇寸殑 key 鍚嶇О銆?
     */
    private class KeyspacePermissionMessageListener implements MessageListener {

        @Override
        public void onMessage(@org.jspecify.annotations.NonNull Message message, @org.jspecify.annotations.Nullable byte[] pattern) {
            try {
                String channel = new String(message.getChannel());
                String key = new String(message.getBody());

                if (log.isDebugEnabled()) {
                    log.debug("鏀跺埌 Redis Keyspace 浜嬩欢: channel={}, key={}", channel, key);
                }

                // 鍖归厤 role 鐩稿叧 key
                Matcher matcher = ROLE_KEY_PATTERN.matcher(key);
                if (matcher.find()) {
                    String roleCode = matcher.group(1);
                    if (StringUtils.isNotBlank(roleCode)) {
                        log.info("Redis Keyspace 浜嬩欢瑙﹀彂鏉冮檺缂撳瓨澶辨晥: key={}, roleCode={}", key, roleCode);
                        permissionEvaluator.clearCachesByRoleCodes(roleCode);
                    }
                }
            } catch (Exception e) {
                log.error("澶勭悊 Redis Keyspace 浜嬩欢寮傚父: {}", e.getMessage(), e);
            }
        }
    }
}
