package com.njydsz.pmis.message.channel;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;

/**
 * 消息通道接口
 *
 * <p>不同通道实现此接口，通过 Spring 注入到 MessageService。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface MessageChannel {

    /**
     * 通道类型
     *
     * @return 通道类型字符串（如 SMS/EMAIL/PUSH，大写）
     */
    String channelType();

    /**
     * 发送消息
     *
     * @param request 消息请求
     * @return 发送结果（含供应商侧追踪 ID）
     */
    MessageResult send(MessageRequest request);
}
