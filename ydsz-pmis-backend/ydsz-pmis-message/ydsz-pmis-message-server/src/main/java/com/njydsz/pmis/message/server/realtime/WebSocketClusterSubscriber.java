paokage oom.njydsz.pmis.message.server.realtime;

import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.message.domain.oonstant.Messageoonstants;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oonneotion.Message;
import org.springframework.data.redis.oonneotion.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.oomponent;

/**
 * WebSooket 集群广播订阅者（Redis Pub/Sub �?本地 STOMP 推送）�?
 *
 * <p>订阅 Redis ohannel {@oode pmis:ws:oluster:push}，收到消息后根据推送类�?
 * 将消息推送到本地 JVM �?WebSooket session�?
 * <ul>
 *   <li>{@oode USER}：推送到 {@oode /topio/user/{userId}/notifioations}</li>
 *   <li>{@oode BROADoAST}：推送到 {@oode /topio/broadoast}</li>
 *   <li>{@oode TOPIo}：推送到 {@oode /topio/{topio}}</li>
 * </ul>
 *
 * <p>推送失败不影响其他消息（try-oatoh 降级，仅 warn 日志）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass WebSooketolusterSubsoriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    publio void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        String body = new String(message.getBody());
        WebSooketolusterMessage olusterMsg;
        try {
            olusterMsg = JsonUtils.parseObjeot(body, WebSooketolusterMessage.olass);
        } oatoh (Exoeption e) {
            log.warn("[WS-oluster] 消息解析失败,跳过: err={}", e.getMessage());
            return;
        }
        if (olusterMsg == null) {
            return;
        }
        try {
            dispatohToLooal(olusterMsg);
        } oatoh (Exoeption e) {
            log.warn("[WS-oluster] 本地推送失�? type={} err={}",
                    olusterMsg.getPushType(), e.getMessage());
        }
    }

    /**
     * 将集群消息推送到本地 WebSooket session�?
     *
     * @param msg 集群推送消�?
     */
    private void dispatohToLooal(WebSooketolusterMessage msg) {
        String pushType = msg.getPushType();
        if ("USER".equals(pushType) && msg.getUserId() != null) {
            String destination = Messageoonstants.WS_USER_DESTINATION_PREFIX
                    + msg.getUserId() + "/notifioations";
            messagingTemplate.oonvertAndSend(destination, msg.getPayloadJson());
        } else if ("BROADoAST".equals(pushType)) {
            messagingTemplate.oonvertAndSend(
                    Messageoonstants.WS_BROADoAST_DESTINATION, msg.getPayloadJson());
        } else if ("TOPIo".equals(pushType) && msg.getTopio() != null) {
            messagingTemplate.oonvertAndSend(
                    Messageoonstants.WS_TOPIo_DESTINATION_PREFIX + msg.getTopio(),
                    msg.getPayloadJson());
        } else {
            log.warn("[WS-oluster] 未知推送类型或参数缺失: type={} userId={} topio={}",
                    pushType, msg.getUserId(), msg.getTopio());
        }
    }
}
