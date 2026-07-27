package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息发送日志视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String channel;
    private String bizType;
    private String bizId;
    private String receiver;
    private String templateCode;
    private String templateParams;
    private String content;
    private String status;
    private String errorMessage;
    private String priority;
    private String senderId;
    private String messageGroup;
    private String batchId;
    private String routeRuleId;
    private Integer canary;
    private String canaryKey;
    private String dedupKey;
    private String recallStatus;
    private LocalDateTime recallAt;
    private String receiptStatus;
    private LocalDateTime receiptAt;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String providerTraceId;
    private Long costMs;
    private BigDecimal cost;
    private String traceId;
    private String msgId;
    private String topic;
    private Integer reconsumeTimes;
    private String parentMsgId;
    private LocalDateTime scheduledAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
