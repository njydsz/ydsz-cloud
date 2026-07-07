package com.njydsz.pmis.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 通知实时推送 Feign 客户端。
 *
 * <p>供 project 等非 system 模块调用 system 的 WebSocket 推送接口，
 * 将站内通知/告警实时下发给前端。FallbackFactory 确保推送失败不影响主业务。
 *
 * <p>使用 contextId 与已有 {@link NotificationClient} 区分（二者均指向 ydsz-pmis-system）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@FeignClient(name = "ydsz-pmis-system", contextId = "pushClient",
        fallbackFactory = NotificationPushClientFallbackFactory.class)
public interface NotificationPushClient {

    /**
     * 向指定用户推送实时消息
     *
     * @param userId  接收用户 ID
     * @param type    消息类型 (NOTIFICATION/ALERT/DASHBOARD)
     * @param payload 消息内容
     * @return 推送结果
     */
    @PostMapping("/notifications/push")
    Map<String, Object> pushToUser(@RequestParam("userId") Long userId,
                                   @RequestParam("type") String type,
                                   @RequestBody Object payload);

    /**
     * 向所有在线用户广播消息
     *
     * @param type    消息类型
     * @param payload 消息内容
     * @return 推送结果
     */
    @PostMapping("/notifications/broadcast")
    Map<String, Object> broadcast(@RequestParam("type") String type,
                                  @RequestBody Object payload);
}
