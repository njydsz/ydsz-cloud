package com.njydsz.message.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 多租户消息配置 DTO（命令请求参数）。
 *
 * <p>用于 Repository 层 CUD 操作的统一入参，不区分 Create / Update：
 * <ul>
 *   <li>创建场景：{@code id} 字段不传</li>
 *   <li>更新场景：传入 {@code id}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class MsgTenantConfigDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 配置唯一标识（主键，更新时传入） */
  private String id;

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
