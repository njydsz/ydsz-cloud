package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 用户消息偏好视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgPreferenceVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    private String channel;
    private String bizType;
    private Integer enabled;
    private Integer dndEnabled;
    private String dndStart;
    private String dndEnd;
    private Integer dailyLimit;
    private Integer hourlyLimit;
    private Integer digestEnabled;
    private String digestFrequency;
    private String locale;
    private String extra;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
