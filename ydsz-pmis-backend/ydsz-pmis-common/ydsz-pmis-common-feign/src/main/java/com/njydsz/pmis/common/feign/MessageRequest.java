package com.njydsz.pmis.common.feign;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 消息发送请求 DTO（兼容旧 com.njydsz.pmis.common.feign.MessageRequest）。
 *
 * <p>封装消息发送所需的全部信息，支持多通道路由。
 *
 * @author ydsz-pmis-team
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

    /** 附加参数（模板变量、Webhook 配置等） */
    private Map<String, Object> params;
}
