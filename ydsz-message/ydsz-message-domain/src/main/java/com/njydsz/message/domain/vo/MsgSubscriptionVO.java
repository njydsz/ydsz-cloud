package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 订阅关系视图对象（VO）。
 *
 * <p>用于 Controller 层返回用户订阅关系的完整信息，包含订阅主题、通道、 状态及退订时间，支撑消息订阅管理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgSubscriptionVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 订阅记录唯一标识（主键） */
  private String id;

  /** 用户 ID */
  private String userId;

  /** 订阅主题编码 */
  private String topicCode;

  /** 接收通道 */
  private String channel;

  /** 状态（SUBSCRIBED/UNSUBSCRIBED） */
  private String status;

  /** 角色范围 */
  private String roleScope;

  /** 扩展配置（JSON） */
  private String extra;

  /** 退订时间 */
  private LocalDateTime unsubscribedAt;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
