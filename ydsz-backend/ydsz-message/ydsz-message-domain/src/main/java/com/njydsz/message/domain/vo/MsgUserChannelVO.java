package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 用户通道绑定视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgUserChannelVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    private String channelType;
    private String channelUserId;
    private Integer verified;
    private Integer isPrimary;
    private String extra;
    private String status;
    private String createdBy;
    private java.time.LocalDateTime createdAt;
    private String updatedBy;
    private java.time.LocalDateTime updatedAt;
}
