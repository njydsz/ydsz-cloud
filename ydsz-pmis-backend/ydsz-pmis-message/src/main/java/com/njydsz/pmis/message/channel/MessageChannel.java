package com.njydsz.pmis.message.channel;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;

/**
 * 消息通道 SPI 接口。
 *
 * <p>不同通道（SMS/EMAIL/PUSH/IN_APP/WEBHOOK/DINGTALK/WECOM/FEISHU）实现此接口，
 * 由 {@link ChannelRouter} 统一收集、路由与分发。通道类型字符串需与
 * {@link com.njydsz.pmis.message.enums.MessageChannelEnum} 枚举名保持一致（大写）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface MessageChannel {

    /**
     * 通道类型，大写，与 {@link com.njydsz.pmis.message.enums.MessageChannelEnum} 一致。
     *
     * @return 通道类型字符串（如 SMS / EMAIL / DINGTALK）
     */
    String channelType();

    /**
     * 发送消息。
     *
     * @param request 消息请求
     * @return 发送结果（含供应商侧追踪 ID），失败时返回 {@code fail}
     */
    MessageResult send(MessageRequest request);
}
