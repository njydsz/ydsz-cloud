package com.njydsz.pmis.notification.feign;

import com.njydsz.pmis.common.api.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 消息服务 Feign 客户端（通知模块专用）
 *
 * <p>用于通知发送时调用消息服务（邮件/短信/推送）进行实际投递。
 */
@FeignClient(name = "ydsz-pmis-message", fallbackFactory = MessageServiceClientFallback.class)
public interface MessageServiceClient {

    /**
     * 发送消息（支持模板渲染）
     */
    @PostMapping("/api/v1/message/send")
    R<Object> send(@RequestBody MessageFeignDTO dto);

    /**
     * 已注册通道列表
     */
    @GetMapping("/api/v1/message/channels")
    R<List<String>> channels();

    /**
     * 通知发送所需的最小 DTO
     */
    @lombok.Data
    class MessageFeignDTO {
        private String channel;
        private String templateCode;
        private String receiver;
        private java.util.Map<String, Object> params;
        private String content;
        private String subject;
        private String bizType;
        private String bizId;
    }
}
