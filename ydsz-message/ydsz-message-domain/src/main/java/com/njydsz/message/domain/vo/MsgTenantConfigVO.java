package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 多租户消息配置视图对象（VO）。
 *
 * <p>用于 Controller 层返回多租户消息配置的完整信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgTenantConfigVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 配置唯一标识（主键） */
  private String id;

  /** 租户 ID */
  private String tenantId;

  /** 租户名称 */
  private String tenantName;

  /** 租户级每日发送上限（null 表示使用全局默认值） */
  private Long dailyLimit;

  /** 租户级每小时发送上限（null 表示使用全局默认值） */
  private Long hourlyLimit;

  /** 租户级通道开关：JSON Map */
  private String channelOverrides;

  /** 租户级通道映射：JSON Map */
  private String providerOverrides;

  /** 配置状态：ENABLED / DISABLED */
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
