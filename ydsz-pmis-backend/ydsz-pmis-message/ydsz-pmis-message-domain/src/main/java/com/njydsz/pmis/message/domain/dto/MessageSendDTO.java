package com.njydsz.pmis.message.domain.dto.core;


import com.njydsz.pmis.common.safe.annotation.Xss;
import lombok.Data;

import java.util.Map;

/**
 * 消息直接发送 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class MessageSendDTO {

    /** 通道 */
    private String channel;

    /** 模板编码 */
    private String templateCode;

    /** 接收人 */
    private String receiver;

    /** 模板参数(用于占位符渲染) */
    private Map<String, Object> params;

    /** 直接发送的内容(不走模板) */
    @Xss(message = "消息内容包含非法字符")
    private String content;

    /** 邮件主题(仅 EMAIL) */
    @Xss(message = "邮件主题包含非法字符")
    private String subject;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 发送优先级 */
    private String priority;

    /** 消息唯一标识(用于幂等去重) */
    private String messageId;

    /** 触发发送的用户 ID */
    private String senderId;

    /** 聚合组 */
    private String messageGroup;

    /** 语言区域 */
    private String locale;
}
