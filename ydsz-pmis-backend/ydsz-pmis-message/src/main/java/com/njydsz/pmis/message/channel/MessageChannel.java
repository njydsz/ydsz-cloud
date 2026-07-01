package com.njydsz.pmis.message.channel;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;

/**
 * 消息通道接口
 *
 * <p>不同通道实现此接口，通过 Spring 注入到 MessageService。
 */
public interface MessageChannel {

    /**
     * 通道类型
     */
    String channelType();

    /**
     * 发送消息
     */
    MessageResult send(MessageRequest request);
}
