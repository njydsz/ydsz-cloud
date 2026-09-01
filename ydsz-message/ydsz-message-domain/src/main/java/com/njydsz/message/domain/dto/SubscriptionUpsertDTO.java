package com.njydsz.message.domain.dto;

import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 订阅关系新增/更新 DTO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class SubscriptionUpsertDTO {

  /** 用户 ID */
  @Xss private String userId;

  /** 主题编码 */
  @Xss private String topicCode;

  /** 通道 */
  @Xss private String channel;

  /** 订阅状态: SUBSCRIBED/UNSUBSCRIBED */
  @Xss private String status;

  /** 角色范围 */
  @Xss private String roleScope;

  /** 扩展字段 JSON */
  @Xss private String extra;
}
