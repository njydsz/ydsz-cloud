package com.njydsz.pmis.execution.feign;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 消息中心 OpenFeign 客户端
 *
 * <p>执行模块在预警分发 / 工单通知等场景通过该客户端调用消息中心；
 * 当 ydsz-pmis-message 不可用时, FallbackFactory 返回默认值避免级联失败.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-message", fallbackFactory = MessageServiceClientFallback.class)
public interface MessageServiceClient {

    @PostMapping("/api/v1/message/send")
    R<MessageResult> send(@RequestBody MessageRequest request);
}
