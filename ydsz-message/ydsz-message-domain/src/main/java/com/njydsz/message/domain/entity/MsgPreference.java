package com.njydsz.message.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 用户消息偏好表: 免打扰时段 / 频率上限 / 聚合开关 / 偏好语言
 *
 * @author ydsz-team
 * @since 26.09.01
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_preference")
public class MsgPreference extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID(关联 ydsz_employee.id) */
  private String userId;

  /** 通道: SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WECOM/FEISHU */
  private String channel;

  /** 业务类型(__DEFAULT__ 表示该通道全局默认偏好) */
  private String bizType;

  /** 是否启用该通道: 0 关闭 / 1 开启(关闭后不发送) */
  private Integer enabled;

  /** 免打扰开关: 0 关闭 / 1 开启 */
  private Integer dndEnabled;

  /** 免打扰开始时间 HH:mm(如 22:00) */
  private String dndStart;

  /** 免打扰结束时间 HH:mm(如 08:00) */
  private String dndEnd;

  /** 每日发送上限(超过则暂存或丢弃) */
  private Integer dailyLimit;

  /** 每小时发送上限 */
  private Integer hourlyLimit;

  /** 聚合开关: 0 即时发送 / 1 聚合摘要 */
  private Integer digestEnabled;

  /** 聚合频率: HOURLY / DAILY / WEEKLY */
  private String digestFrequency;

  /** 偏好语言(如 zh-CN / en-US,影响模板 i18n 选择) */
  private String locale;

  /** 扩展字段 JSON */
  private String extra;
}
