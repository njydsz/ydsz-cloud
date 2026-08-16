package com.njydsz.common.feign;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 消息发送请求 DTO（兼容旧 com.njydsz.common.feign.MessageRequest）。
 *
 * <p>封装消息发送所需的全部信息，支持多通道路由。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MessageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息通道：INAPP / EMAIL / WEBHOOK / SMS */
    private String channel;

    /** 接收者标识（用户ID / 邮箱 / Webhook URL 等） */
    private String receiver;

    /** 消息主题 */
    private String subject;

    /** 消息内容 */
    private String content;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 模板编码 */
    private String templateCode;

    /** 消息 ID（业务侧生成，用于全链路追踪） */
    private String messageId;

    /** 附加参数（模板变量、Webhook 配置等） */
    private Map<String, Object> params;

    /** 通道元数据（signName、providerKey、attachments 等） */
    private Map<String, String> channelMeta;

    /** 定时发送时间 */
    private LocalDateTime scheduledAt;

    /** 发送优先级（URGENT / HIGH / NORMAL / LOW） */
    private String priority;

    /** 父消息 ID（级联发送时使用） */
    private String parentMsgId;

    /** 级联子消息列表 */
    private List<MessageRequest> cascadeTo;
}
