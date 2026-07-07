package com.njydsz.pmis.system.feign;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 消息服务 Feign 客户端（通知模块专用）
 *
 * <p>用于通知发送时调用消息服务（邮件/短信/推送）进行实际投递。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-system", fallbackFactory = MessageServiceClientFallback.class)
public interface MessageServiceClient {

    /**
     * 发送消息（支持模板渲染）
     *
     * @param dto 消息发送 DTO
     * @return 统一响应结果，包含供应商追踪 ID
     */
    @PostMapping("/message/send")
    Result<Object> send(@RequestBody MessageFeignDTO dto);

    /**
     * 已注册通道列表
     *
     * @return 统一响应结果，包含通道名称列表
     */
    @GetMapping("/message/channels")
    Result<List<String>> channels();

    /**
     * 通知发送所需的最小 DTO
     */
    @lombok.Data
    class MessageFeignDTO {
        /** 投递通道（EMAIL/SMS/PUSH） */
        private String channel;
        /** 模板编码（可选） */
        private String templateCode;
        /** 接收人（邮箱/手机号/用户 ID） */
        private String receiver;
        /** 模板参数 */
        private Map<String, Object> params;
        /** 文本内容（无模板时使用） */
        private String content;
        /** 主题（邮件使用） */
        private String subject;
        /** 业务类型 */
        private String bizType;
        /** 业务单据 ID */
        private String bizId;
    }
}
