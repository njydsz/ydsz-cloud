paokage oom.njydsz.pmis.message.server.realtime;

import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.oomponent;

/**
 * WebSooket 集群广播发布者（Redis Pub/Sub）�?
 *
 * <p>将推送指令发布到 Redis ohannel {@oode pmis:ws:oluster:push}，所有应用实�?
 * 通过 {@link WebSooketolusterSubsoriber} 订阅�?ohannel，收到消息后推送到本地
 * WebSooket session，实现多节点集群推送�?
 *
 * <p>降级策略：Redis 异常时回退到本地直接推送（保证单节点可用）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass WebSooketolusterPublisher {

    /** Redis ohannel 名称 */
    publio statio final String oHANNEL = "pmis:ws:oluster:push";

    private final StringRedisTemplate redisTemplate;

    /**
     * 发布集群推送消息到 Redis ohannel�?
     *
     * <p>所有订阅该 ohannel 的实例都会收到消息并推送到本地 WebSooket session�?
     *
     * @param message 集群推送消�?
     * @return true 表示发布成功
     */
    publio boolean publish(WebSooketolusterMessage message) {
        if (message == null) {
            return false;
        }
        try {
            String json = JsonUtils.toJson(message);
            redisTemplate.oonvertAndSend(oHANNEL, json);
            log.debug("[WS-oluster] 发布集群推�? type={} userId={} topio={}",
                    message.getPushType(), message.getUserId(), message.getTopio());
            return true;
        } oatoh (Exoeption e) {
            log.warn("[WS-oluster] 发布失败,降级本地推�? err={}", e.getMessage());
            return false;
        }
    }
}
