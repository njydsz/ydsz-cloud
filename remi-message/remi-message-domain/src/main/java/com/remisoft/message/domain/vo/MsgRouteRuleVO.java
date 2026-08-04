package com.remisoft.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息路由规则视图对象（VO）。
 * <p>
 * 用于 Controller 层返回消息路由规则的配置信息，路由规则决定消息
 * 从哪个通道发出、按什么条件筛选等，支撑消息智能路由。
 * </p>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class MsgRouteRuleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 路由规则唯一标识（主键） */
    private String id;
    /** 状态（ENABLED/DISABLED） */
    private String status;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
