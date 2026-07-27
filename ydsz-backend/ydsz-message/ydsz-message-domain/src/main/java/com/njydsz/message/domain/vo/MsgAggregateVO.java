package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 聚合批次视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgAggregateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String aggregateGroup;
    private String receiver;
    private String channel;
    private String batchStatus;
    private Integer messageCount;
    private LocalDateTime firstMessageAt;
    private LocalDateTime lastMessageAt;
    private LocalDateTime scheduledSendAt;
    private LocalDateTime sentAt;
    private String digestContent;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
