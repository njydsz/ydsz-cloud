package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 消息服务 Feign 客户端。
 *
 * <p>对接 message 模块 {@code POST /api/v1/message/send}，支持 EMAIL / SMS / PUSH 通道。
 * 入参使用 {@link Map} 以避免跨模块 DTO 依赖（字段对齐 MessageSendDTO）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(
        name = "ydsz-pmis-message",
        contextId = "messageFeignClient",
        path = "/api/v1/message",
        fallbackFactory = MessageFeignClientFallbackFactory.class
)
public interface MessageFeignClient {

    /**
     * 发送消息（支持模板渲染 / 直发内容）。
     *
     * @param dto 消息参数（channel / receiver / subject / content / templateCode / params / bizType / bizId）
     * @return 发送结果
     */
    @PostMapping("/send")
    Result<Map<String, Object>> send(@RequestBody Map<String, Object> dto);
}
