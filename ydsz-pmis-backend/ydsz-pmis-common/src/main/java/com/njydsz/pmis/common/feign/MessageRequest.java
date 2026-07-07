package com.njydsz.pmis.common.feign;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 消息发送请求（跨模块共享 DTO）
 *
 * <p>执行模块在预警分发 / 工单通知等场景通过该 DTO 调用消息中心。
 * 放在 common 模块避免 execution 直接依赖 message 模块。
 *
 * <p>P2-6: 支持 {@link #cascadeTo} 级联发送,父消息发送成功后自动触发子消息。
 * 级联深度上限由消息引擎控制(默认 5 层),超过则忽略并记 WARN 日志。
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

    /** 消息唯一标识（P0-6: 用于消费端幂等去重，producer 可生成 UUID 填入） */
    private String messageId;

    /**
     * P2-6: 级联消息列表。
     *
     * <p>父消息发送成功后,按列表顺序逐条发送级联消息。
     * 级联消息的 {@code parentMsgId} 自动设置为父消息的 {@code msgId}。
     * 单条级联消息失败不影响其他级联消息。
     */
    private List<MessageRequest> cascadeTo;

    /**
     * P2-6: 父消息 ID（引擎内部使用,调用方无需设置）。
     *
     * <p>由消息引擎在级联发送时自动填充,用于追溯级联关系。
     */
    private String parentMsgId;
}
