package com.njydsz.message.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * 多租户消息配置实体，提供租户级发送配额与通道覆盖能力。
 *
 * <p>对应数据库表 {@code ydsz_msg_tenant_config}。每个租户可独立配置发送配额
 * （dailyLimit/hourlyLimit）、通道开关覆盖（channelOverrides）和
 * 通道映射覆盖（providerOverrides），与 common-tenant 逻辑隔离互补：
 * common-tenant 负责数据层过滤，本实体负责业务层配额与通道策略。
 *
 * @author ydsz
 * @since 26.09.24
 */@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_tenant_config")
public class MsgTenantConfig extends MpBaseIdEntity<String> {

  /** 租户 ID */
  private String tenantId;

  /** 租户名称 */
  private String tenantName;

  /** 租户级每日发送上限（null 表示使用全局默认值） */
  private Long dailyLimit;

  /** 租户级每小时发送上限（null 表示使用全局默认值） */
  private Long hourlyLimit;

  /** 租户级通道开关：JSON Map，如 {"SMS": true, "EMAIL": false} */
  private String channelOverrides;

  /** 租户级通道映射：JSON Map，如 {"SMS": "aliyun", "EMAIL": "sendgrid"} */
  private String providerOverrides;

  /** 配置状态：ENABLED / DISABLED */
  private String status;
}
