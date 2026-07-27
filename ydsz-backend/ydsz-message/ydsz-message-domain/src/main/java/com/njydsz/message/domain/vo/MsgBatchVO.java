package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息发送批次视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgBatchVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String batchId;
    private String batchName;
    private String channel;
    private String templateCode;
    private String bizType;
    private Integer total;
    private Integer success;
    private Integer failed;
    private Integer skipped;
    private String status;
    private String audienceSource;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String senderId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
