package com.njydsz.common.feign.fallback;

import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.feign.NotificationClient;
import com.njydsz.common.feign.dto.NotificationFeignDTO;
import com.njydsz.common.feign.dto.RealtimePushDTO;

/**
 * NotificationClient 降级工厂。
 *
 * <p>当 message 服务不可用时，通知发送、消息发送和实时推送降级为记录日志并返回失败响应，
 * 不影响主业务流程。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class NotificationClientFallbackFactory extends DefaultFallbackFactory<NotificationClient> {

    @Override
    protected NotificationClient createFallback(Throwable cause) {
        return new NotificationClient() {
            @Override
            public BaseResponse<Void> send(NotificationFeignDTO dto) {
                log.warn("NotificationClient.send 降级: dto={}, cause={}", dto, cause.getMessage());
                return BaseResponse.error("B01004", "通知服务暂时不可用");
            }

            @Override
            public BaseResponse<MessageResult> sendMessage(MessageRequest request) {
                log.warn("NotificationClient.sendMessage 降级: bizId={}, cause={}",
                        request.getBizId(), cause.getMessage());
                return BaseResponse.error("B01004", "消息服务暂时不可用");
            }

            @Override
            public BaseResponse<Void> pushRealtime(String userId, String type, RealtimePushDTO dto) {
                log.warn("NotificationClient.pushRealtime 降级: userId={}, type={}, cause={}",
                        userId, type, cause.getMessage());
                return BaseResponse.error("B01004", "实时推送服务暂时不可用");
            }

            @Override
            public BaseResponse<Void> broadcast(String type, RealtimePushDTO dto) {
                log.warn("NotificationClient.broadcast 降级: type={}, cause={}", type, cause.getMessage());
                return BaseResponse.error("B01004", "广播服务暂时不可用");
            }
        };
    }
}
