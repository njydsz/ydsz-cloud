package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.channel.MessageChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 站内信通道实现。
 *
 * <p>站内信的实际入库由 {@code NotificationService} 负责（落库 {@code pmis_msg_notification} 表），
 * 本通道仅返回成功结果并记录日志，作为通道框架下的统一发送出口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class InAppChannel implements MessageChannel {

    /** 通道类型 */
    private static final String CHANNEL_TYPE = "IN_APP";

    /**
     * 通道类型。
     *
     * @return IN_APP
     */
    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    /**
     * 站内信发送：仅记录日志并返回成功结果，实际入库由 NotificationService 负责。
     *
     * @param request 消息请求
     * @return 发送结果（含追踪 ID）
     */
    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail(CHANNEL_TYPE, "站内信接收人不能为空");
        }
        String traceId = "IN_APP-" + SnowflakeIdGenerator.nextTraceId();
        log.info("[IN_APP] 站内信 receiver={} bizType={} content={}",
                request.getReceiver(), request.getBizType(), request.getContent());
        return MessageResult.ok(CHANNEL_TYPE, traceId);
    }
}
