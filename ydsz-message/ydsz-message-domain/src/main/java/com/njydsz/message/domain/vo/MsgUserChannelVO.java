package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户通道绑定视图对象（VO）。
 *
 * <p>用于 Controller 层返回用户在各通道的身份绑定信息，@包含通道用户 ID、 验证状态和主通道标记，支撑多通道消息投递。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgUserChannelVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 绑定记录唯一标识（主键） */
  private String id;

  /** 用户 ID */
  private String userId;

  /** 通道类型（SMS/EMAIL/WECHAT/WEBHOOK） */
  private String channelType;

  /** 通道侧用户 ID（手机号/邮箱/openid） */
  private String channelUserId;

  /** 是否已验证（1=已验证，0=未验证） */
  private Integer verified;

  /** 是否主通道（1=主通道，0=备用） */
  private Integer isPrimary;

  /** 扩展配置（JSON） */
  private String extra;

  /** 状态（ACTIVE/INACTIVE） */
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
