package com.njydsz.pmis.common.feign;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 消息发送请求（跨模块共享 DTO）
 *
 * <p>执行模块在预警分发 / 工单通知等场景通过该 DTO 调用消息中心。
 * 放在 common 模块避免 execution 直接依赖 message 模块。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    /** 通道: SMS/EMAIL/PUSH */
    private String channel;

    /** 模板编码 */
    private String templateCode;

    /** 接收人 */
    private String receiver;

    /** 模板参数（用于占位符渲染） */
    private Map<String, Object> params;

    /** 直接发送的内容（不走模板） */
    private String content;

    /** 邮件主题（仅 EMAIL） */
    private String subject;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;
}
