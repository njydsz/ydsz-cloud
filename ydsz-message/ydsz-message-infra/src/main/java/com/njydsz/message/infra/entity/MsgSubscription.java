package com.njydsz.message.infra.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 订阅关系表: 用户对主题(topic_code)在指定通道的订阅/退订状态
 *
 * @author ydsz-team
 * @since 26.09.01
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_subscription")
public class MsgSubscription extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

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

  /** 退订时间（P1-5：仅当 status=UNSUBSCRIBED 时有意义；SUBSCRIBED 时为 null） */
  private LocalDateTime unsubscribedAt;
}
