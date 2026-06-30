package com.njydsz.pmis.message.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 消息发送请求
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
