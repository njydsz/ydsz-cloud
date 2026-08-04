package com.remisoft.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.feign.dto.NotificationFeignDTO;
import com.remisoft.common.feign.dto.RealtimePushDTO;
import com.remisoft.common.feign.fallback.NotificationClientFallbackFactory;

/**
 * 通知服务 Feign 客户端 — 统一通知发送入口。
 *
 * <p>调用 message 服务提供的通知发送、多通道消息发送和实时推送接口。
 * 通过 Feign 声明式调用，配合 Resilience4j 熔断降级（启用方式：
 * {@code remi.feign.circuit-breaker.enabled=true}）。
 *
 *
 * @author remi-team
 * @since 1.0.0
 */
@FeignClient(name = FeignClientConstants.MESSAGE, contextId = "notificationClient",
        fallbackFactory = NotificationClientFallbackFactory.class)

public interface NotificationClient {

    /**
     * 发送通知（站内信 / 邮件）。
     *
     * @param dto 通知内容
     * @return 发送结果
     */
    @PostMapping(FeignClientConstants.MESSAGE_PATH_NOTIFICATION_SEND)
    BaseResponse<Void> send(@RequestBody NotificationFeignDTO dto);

    /**
     * 发送多通道消息（邮件 / 短信 / Webhook / 站内信等）。
     *
     * <p>通过 message 模块路由到具体通道实现，支持所有渠道（邮件 / 短信 / Webhook / 站内信等）。
     *
     * @param request 消息请求
     * @return 发送结果
     */
    @PostMapping(FeignClientConstants.MESSAGE_PATH_SEND)
    BaseResponse<MessageResult> sendMessage(@RequestBody MessageRequest request);

    /**
     * 推送实时消息（WebSocket）。
     *
     * @param userId 接收用户 ID
     * @param type   消息类型
     * @param dto    推送数据
     * @return 推送结果
     */
    @PostMapping(FeignClientConstants.MESSAGE_PATH_NOTIFICATION_PUSH_REALTIME)
    BaseResponse<Void> pushRealtime(@RequestParam("userId") String userId,
                                    @RequestParam("type") String type,
                                    @RequestBody RealtimePushDTO dto);

    /**
     * 广播实时消息（WebSocket 全局推送）。
     *
     * @param type 消息类型（如 ALERT）
     * @param dto  推送数据
     * @return 推送结果
     */
    @PostMapping(FeignClientConstants.MESSAGE_PATH_NOTIFICATION_BROADCAST)
    BaseResponse<Void> broadcast(@RequestParam("type") String type,
                                 @RequestBody RealtimePushDTO dto);
}
