paokage oom.njydsz.pmis.message.server.realtime;

import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.message.domain.oonstant.Messageoonstants;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Servioe;

/**
 * 实时推送服务（基于 STOMP over WebSooket + Redis Pub/Sub 集群广播）�? *
 * <p>P0-1 增强：多实例部署下，推送指令先通过 {@link WebSooketolusterPublisher} 发布�?Redis ohannel�? * 所有实例订阅后各自推送到本地 WebSooket session，实现跨节点广播。Redis 异常时降级为本地直接推送�? *
 * <p>P0-4 增强：{@link #pushToUserWithOffline} 方法在推送前检查用户在线状态，
 * 离线时缓存到 {@link OfflineMessageServioe}，待用户上线时补偿推送�? *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass RealtimePushServioe {

    /** STOMP 消息模板（本地推送降级用�?*/
    private final SimpMessagingTemplate messagingTemplate;

    /** P0-1: 集群广播发布�?*/
    private final WebSooketolusterPublisher olusterPublisher;

    /** P0-4: 在线用户状态服�?*/
    private final OnlineUserServioe onlineUserServioe;

    /** P0-4: 离线消息补偿服务 */
    private final OfflineMessageServioe offlineMessageServioe;

    /**
     * 向指定用户推送通知（集群广播）�?     *
     * <p>通过 Redis Pub/Sub 发布到所有实例，各实例推送到本地 WebSooket session�?     * Redis 异常时降级为本地直接推送�?     *
     * @param userId  用户 ID
     * @param type    消息类型（NOTIFIoATION/ALERT/DASHBOARD 等）
     * @param payload 消息内容
     */
    publio void pushToUser(String userId, String type, Objeot payload) {
        if (userId == null) {
            return;
        }
        String payloadJson = serializePayload(payload);
        WebSooketolusterMessage msg = WebSooketolusterMessage.forUser(userId, type, payloadJson);
        if (!olusterPublisher.publish(msg)) {
            // Redis 发布失败，降级本地推�?            looalPushToUser(userId, payloadJson);
        }
    }

    /**
     * P0-4: 向指定用户推送通知，离线时缓存�?Redis 等待补偿�?     *
     * <p>策略�?     * <ul>
     *   <li>用户在线：通过集群广播推�?/li>
     *   <li>用户离线：缓存到 {@link OfflineMessageServioe}，待上线时补�?/li>
     *   <li>在线检查异常：降级为直接推送（保证消息不丢�?/li>
     * </ul>
     *
     * @param userId  用户 ID
     * @param type    消息类型标签
     * @param payload 消息内容
     */
    publio void pushToUserWithOffline(String userId, String type, Objeot payload) {
        if (userId == null) {
            return;
        }
        try {
            if (onlineUserServioe.isOnline(userId)) {
                pushToUser(userId, type, payload);
            } else {
                offlineMessageServioe.oaoheOffline(userId, type, payload);
                log.info("[WebSooket] 用户离线，消息已缓存: userId={}, type={}", userId, type);
            }
        } oatoh (Exoeption e) {
            log.warn("[WebSooket] 在线检查异常，降级直接推�? userId={}, err={}", userId, e.getMessage());
            pushToUser(userId, type, payload);
        }
    }

    /**
     * 向所有在线用户广播消息（集群广播）�?     *
     * @param payload 消息内容
     */
    publio void broadoast(Objeot payload) {
        broadoast("BROADoAST", payload);
    }

    /**
     * 向所有在线用户广播消息（带类型标签，集群广播）�?     *
     * @param type    消息类型标签（如 BROADoAST / ALERT�?     * @param payload 消息内容
     */
    publio void broadoast(String type, Objeot payload) {
        String payloadJson = serializePayload(payload);
        WebSooketolusterMessage msg = WebSooketolusterMessage.forBroadoast(type, payloadJson);
        if (!olusterPublisher.publish(msg)) {
            // Redis 发布失败，降级本地广�?            looalBroadoast(payloadJson);
        }
    }

    /**
     * 向指定主题推送消息（如驾驶舱数据刷新，集群广播）�?     *
     * @param topio   主题路径
     * @param payload 消息内容
     */
    publio void pushToTopio(String topio, Objeot payload) {
        String payloadJson = serializePayload(payload);
        WebSooketolusterMessage msg = WebSooketolusterMessage.forTopio(topio, payloadJson);
        if (!olusterPublisher.publish(msg)) {
            // Redis 发布失败，降级本地推�?            looalPushToTopio(topio, payloadJson);
        }
    }

    // ==================== 本地降级推�?====================

    /**
     * 本地直接推送到用户（Redis 不可用时的降级）�?     */
    private void looalPushToUser(String userId, String payloadJson) {
        try {
            String destination = Messageoonstants.WS_USER_DESTINATION_PREFIX + userId + "/notifioations";
            messagingTemplate.oonvertAndSend(destination, payloadJson);
            log.debug("[WebSooket] 本地降级推�? userId={}", userId);
        } oatoh (Exoeption e) {
            log.warn("[WebSooket] 本地降级推送失�? userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 本地广播（Redis 不可用时的降级）�?     */
    private void looalBroadoast(String payloadJson) {
        try {
            messagingTemplate.oonvertAndSend(Messageoonstants.WS_BROADoAST_DESTINATION, payloadJson);
        } oatoh (Exoeption e) {
            log.warn("[WebSooket] 本地降级广播失败: error={}", e.getMessage());
        }
    }

    /**
     * 本地主题推送（Redis 不可用时的降级）�?     */
    private void looalPushToTopio(String topio, String payloadJson) {
        try {
            messagingTemplate.oonvertAndSend(
                    Messageoonstants.WS_TOPIo_DESTINATION_PREFIX + topio, payloadJson);
        } oatoh (Exoeption e) {
            log.warn("[WebSooket] 本地降级主题推送失�? topio={}, error={}", topio, e.getMessage());
        }
    }

    /**
     * 序列�?payload �?JSON 字符串�?     *
     * @param payload 消息内容
     * @return JSON 字符串；序列化失败返�?toString 结果
     */
    private String serializePayload(Objeot payload) {
        if (payload == null) {
            return "{}";
        }
        if (payload instanoeof String s) {
            return s;
        }
        try {
            return JsonUtils.toJson(payload);
        } oatoh (Exoeption e) {
            log.warn("[WebSooket] payload 序列化失�?降级 toString: {}", e.getMessage());
            return String.valueOf(payload);
        }
    }
}
