package com.njydsz.message.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 多租户消息配置领域实体 — 提供租户级发送配额与通道覆盖能力。
 *
 * <p>对应数据库表 {@code ydsz_msg_tenant_config}。每个租户可独立配置：
 *
 * <ul>
 *   <li><b>发送配额</b>：每日 / 每小时发送上限（{@link #dailyLimit} / {@link #hourlyLimit}）
 *   <li><b>通道开关覆盖</b>：租户级覆盖全局 {@code ydsz.message.channelEnabled}（{@link #channelOverrides}）
 *   <li><b>通道映射覆盖</b>：租户级指定通道使用的服务商（{@link #providerOverrides}）
 * </ul>
 *
 * <p><b>多租户硬隔离（P2-A5）：</b>本实体实现租户级配置覆盖，
 * 与 ydsz-common-tenant 的逻辑隔离（MyBatis 拦截器自动注入 tenantId）互补：
 * common-tenant 负责数据层过滤，本实体负责业务层配额与通道策略。
 *
 * <p><b>字段语义：</b>
 *
 * <ul>
 *   <li>{@code dailyLimit} — 租户级每日发送上限（null 表示使用全局默认值）
 *   <li>{@code hourlyLimit} — 租户级每小时发送上限（null 表示使用全局默认值）
 *   <li>{@code channelOverrides} — JSON Map，如 {"SMS": true, "EMAIL": false}
 *   <li>{@code providerOverrides} — JSON Map，如 {"SMS": "aliyun", "EMAIL": "sendgrid"}
 *   <li>{@code status} — 配置状态：ENABLED / DISABLED
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
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
