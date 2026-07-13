package com.njydsz.pmis.message.domain.entity.config;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 订阅关系表: 用户对主题(topic_code)在指定通道的订阅/退订状态
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_msg_subscription")
public class MsgSubscriptionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID */
    private String userId;

    /** 主题编码(如 RISK_ALERT / CONTRACT_APPROVAL / APPROVAL_TODO) */
    private String topicCode;

    /** 通道 */
    private String channel;

    /** 订阅状态: SUBSCRIBED 已订阅 / UNSUBSCRIBED 已退订 */
    private String status;

    /** 角色范围(如 PM|MEMBER,限定角色内可见性) */
    private String roleScope;

    /** 扩展字段 JSON */
    private String extra;

    /** 租户 ID(单租户部署默认 1) */
    private String tenantId;

    /** 退订时间（P1-5：仅当 status=UNSUBSCRIBED 时有意义；SUBSCRIBED 时为 null） */
    private LocalDateTime unsubscribedAt;
}
