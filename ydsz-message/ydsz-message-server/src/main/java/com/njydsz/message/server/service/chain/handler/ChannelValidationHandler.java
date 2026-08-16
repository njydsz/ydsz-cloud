package com.njydsz.message.server.service.chain.handler;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.entity.config.MsgTrace;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.core.MessageTraceService;

/**
 * 通道启用校验 Handler。
 *
 * <p>校验消息通道是否已启用，未启用时短路管线。
 * 校验通过后记录接收节点轨迹。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class ChannelValidationHandler implements SendHandler {

    private final ChannelRouter channelRouter;
    private final MessageProperties messageProperties;
    private final MessageTraceService messageTraceService;

    @Override
    public boolean handle(MessageRequest request, SendContext ctx) {
        String channel = request.getChannel();
        if (!StringUtils.hasText(channel)) {
            ctx.setErrorResult(MessageResult.fail(null, "消息通道不能为空"));
            return false;
        }
        if (!isChannelEnabled(channel)) {
            log.warn("[Message] 通道未启用: {}", channel);
            ctx.setErrorResult(MessageResult.fail(channel, "通道未启用: " + channel));
            return false;
        }
        // 记录接收节点轨迹
        messageTraceService.recordTrace(
                StringUtils.hasText(request.getMessageId()) ? request.getMessageId()
                        : (StringUtils.hasText(request.getBizId()) ? request.getBizId() : "unknown"),
                MsgTrace.Node.RECEIVED, "SUCCESS", channel,
                "消息已接收: channel=" + channel + " receiver=" + request.getReceiver());
        ctx.setChannel(channel);
        ctx.setReceiver(request.getReceiver());
        ctx.setBizType(request.getBizType());
        ctx.setTemplateCode(request.getTemplateCode());
        return true;
    }

    @Override
    public int order() {
        return 100;
    }

    /**
     * 判断通道是否启用：优先 ChannelRouter，回退 MessageProperties.channelEnabled。
     */
    private boolean isChannelEnabled(String channel) {
        try {
            if (!channelRouter.isChannelEnabled(channel)) {
                return false;
            }
        } catch (Exception e) {
            log.debug("[Message] ChannelRouter 判断异常,回退配置: {}", e.getMessage());
        }
        try {
            Map<String, Boolean> enabled = messageProperties.getChannelEnabled();
            if (enabled != null && enabled.containsKey(channel)) {
                return Boolean.TRUE.equals(enabled.get(channel));
            }
        } catch (Exception e) {
            log.debug("[Message] channelEnabled 配置读取异常: {}", e.getMessage());
        }
        return true;
    }
}
