package com.njydsz.message.domain.dto;

import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 用户消息偏好新增/更新 DTO
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class PreferenceUpsertDTO {

  /** 用户 ID */
  @Xss private String userId;

  /** 通道 */
  @Xss private String channel;

  /** 业务类型 */
  @Xss private String bizType;

  /** 是否启用该通道: true=开启 / false=关闭 */
  private Boolean isEnabled;

  /** 是否开启免打扰: true=开启 / false=关闭 */
  private Boolean isDndEnabled;

  /** 免打扰开始时间 HH:mm */
  @Xss private String dndStart;

  /** 免打扰结束时间 HH:mm */
  @Xss private String dndEnd;

  /** 每日发送上限 */
  private Integer dailyLimit;

  /** 每小时发送上限 */
  private Integer hourlyLimit;

  /** 是否启用聚合: true=聚合 / false=即时发送 */
  private Boolean isDigestEnabled;

  /** 聚合频率: HOURLY/DAILY/WEEKLY */
  @Xss private String digestFrequency;

  /** 偏好语言 */
  @Xss private String locale;

  /** 扩展字段 JSON */
  @Xss private String extra;
}
