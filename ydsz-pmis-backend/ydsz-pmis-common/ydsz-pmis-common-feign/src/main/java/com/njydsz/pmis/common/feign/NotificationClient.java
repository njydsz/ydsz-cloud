package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.feign.dto.NotificationFeignDTO;
import com.njydsz.pmis.common.feign.dto.RealtimePushDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 通知服务 Feign 客户端（兼容旧 com.njydsz.pmis.common.feign.NotificationClient）。
 *
 * <p>调用 message 服务提供的通知发送和实时推送接口。
 * 通过 Feign 声明式调用，配合 Sentinel/Resilience4j 熔断降级。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-message", contextId = "notificationClient")
public interface NotificationClient {

    /**
     * 发送通知（站内信 / 邮件）。
     *
     * @param dto 通知内容
     * @return 发送结果
     */
    @PostMapping("/api/message/notification/send")
    BaseResponse<Void> send(@RequestBody NotificationFeignDTO dto);

    /**
     * 推送实时消息（WebSocket）。
     *
     * @param userId 接收用户 ID
     * @param type   消息类型
     * @param dto    推送数据
     * @return 推送结果
     */
    @PostMapping("/api/message/notification/push-realtime")
    BaseResponse<Void> pushRealtime(@RequestParam("userId") String userId,
                                    @RequestParam("type") String type,
                                    @RequestBody RealtimePushDTO dto);
}
