package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 消息路由规则视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgRouteRuleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String status;
    private String createdBy;
    private java.time.LocalDateTime createdAt;
    private String updatedBy;
    private java.time.LocalDateTime updatedAt;
}
