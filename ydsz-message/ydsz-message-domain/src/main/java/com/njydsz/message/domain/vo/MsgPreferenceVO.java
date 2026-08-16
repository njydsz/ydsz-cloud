package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户消息偏好视图对象（VO）。
 *
 * <p>用于 Controller 层返回用户消息偏好的完整信息，包含通道启停、免打扰时段、 频率限制、摘要配置及语言偏好，支撑用户个性化消息设置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgPreferenceVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 偏好记录唯一标识（主键） */
  private String id;

  /** 用户 ID */
  private String userId;

  /** 通道（SMS/EMAIL/WEBHOOK/WECHAT/INSITE） */
  private String channel;

  /** 业务类型 */
  private String bizType;

  /** 是否启用（1=启用，0=停用） */
  private Integer enabled;

  /** 是否启用免打扰（1=启用，0=停用） */
  private Integer dndEnabled;

  /** 免打扰开始时间（HH:mm 格式） */
  private String dndStart;

  /** 免打扰结束时间（HH:mm 格式） */
  private String dndEnd;

  /** 每日发送上限 */
  private Integer dailyLimit;

  /** 每小时发送上限 */
  private Integer hourlyLimit;

  /** 是否启用摘要聚合（1=启用，0=停用） */
  private Integer digestEnabled;

  /** 摘要频率（HOURLY/DAILY/WEEKLY） */
  private String digestFrequency;

  /** 语言区域 */
  private String locale;

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
