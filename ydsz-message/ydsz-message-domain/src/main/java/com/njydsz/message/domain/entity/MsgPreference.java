package com.njydsz.message.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 用户消息偏好实体，管控接收人的免打扰时段、频率上限、聚合开关及偏好语言。
 *
 * <p>对应数据库表 {@code ydsz_msg_preference}。以 userId + channel + bizType 为粒度配置，
 * bizType=__DEFAULT__ 表示该通道全局偏好。dndStart/dndEnd 定义免打扰时段，
 * dailyLimit/hourlyLimit 控制发送频率，isDigestEnabled 决定是否启用摘要聚合。
 *
 * @author ydsz
 * @since 26.09.24
 */// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合泛型父类继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
@Data
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

  /** 是否启用该通道: true=开启 / false=关闭(关闭后不发送) */
  @TableField("is_enabled")
  private Boolean isEnabled;

  /** 是否开启免打扰: true=开启 / false=关闭 */
  @TableField("is_dnd_enabled")
  private Boolean isDndEnabled;

  /** 免打扰开始时间 HH:mm(如 22:00) */
  private String dndStart;

  /** 免打扰结束时间 HH:mm(如 08:00) */
  private String dndEnd;

  /** 每日发送上限(超过则暂存或丢弃) */
  private Integer dailyLimit;

  /** 每小时发送上限 */
  private Integer hourlyLimit;

  /** 是否启用摘要聚合: true=聚合 / false=即时发送 */
  @TableField("is_digest_enabled")
  private Boolean isDigestEnabled;

  /** 聚合频率: HOURLY / DAILY / WEEKLY */
  private String digestFrequency;

  /** 偏好语言(如 zh-CN / en-US,影响模板 i18n 选择) */
  private String locale;

  /** 扩展字段 JSON */
  private String extra;
}
