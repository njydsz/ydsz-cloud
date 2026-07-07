package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * P1-7: 工作流通知偏好 DO
 *
 * <p>对标钉钉/飞书"免打扰"与"通知聚合"能力。用户可配置：
 * <ul>
 *   <li>免打扰时段（quietHoursStart / quietHoursEnd）：该时段内的通知延迟到时段结束后聚合投递</li>
 *   <li>通知聚合模式（digestMode）：1=启用聚合（免打扰时段内通知合并为一条摘要），0=立即逐条投递</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_notify_preference")
public class FlowNotifyPreferenceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 用户 ID */
    private String userId;

    /** 免打扰开始时间（HH:mm 格式，如 22:00），null 表示不启用 */
    private String quietHoursStart;

    /** 免打扰结束时间（HH:mm 格式，如 08:00），null 表示不启用 */
    private String quietHoursEnd;

    /** 1=启用通知聚合（免打扰时段内通知合并为摘要），0=立即逐条投递 */
    private Integer digestMode;

    /** 链路追踪 ID */
    private String providerTraceId;
}
