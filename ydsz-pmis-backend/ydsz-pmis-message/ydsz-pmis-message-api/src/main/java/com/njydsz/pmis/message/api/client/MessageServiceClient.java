package com.njydsz.pmis.message.api.client;
import com.njydsz.pmis.common.feign.FeignClientConstants;
import com.njydsz.pmis.message.api.fallback.MessageServiceClientFallback;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Message center Feign client (generic message sending).
 *
 * <p>Uses MessageRequest/MessageResult for channel-aware message dispatch.
 * The message module routes to the appropriate channel implementation
 * (EMAIL/SMS/PUSH/DINGTALK/FEISHU/WECOM/WEBHOOK/INAPP) based on the channel field.
 *
 * <p>P0-2 refactor: Moved from project module to common module for shared use.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = FeignClientConstants.MESSAGE, contextId = "messageServiceClient",
        fallbackFactory = MessageServiceClientFallback.class)
public interface MessageServiceClient {

    /**
     * Send message via the specified channel.
     *
     * @param request message request with channel, content, receivers, etc.
     * @return message send result
     */
    @PostMapping("/message/send")
    Result<MessageResult> send(@RequestBody MessageRequest request);
}
