package com.njydsz.system.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import com.njydsz.common.json.annotation.JsonProperty;
import lombok.Data;

/**
 * 特性开关管理视图对象（ydsz-system-domain）。
 *
 * <p>特性开关 CRUD 管理接口的响应 VO，对应前端 FeatureFlagVO。
 *
 * @author ydsz-team
 * @since 26.10.09
 */
@Data
public class FeatureFlagVO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 开关键（全局唯一） */
  private String flagKey;

  /** 开关名称 */
  private String flagName;

  /** 开关类型: BOOLEAN / STRING / JSON */
  private String flagType;

  /** 默认值 */
  private String defaultValue;

  /** 当前生效值 */
  private String currentValue;

  /** 描述 */
  private String description;

  /** 启用状态: ENABLED / DISABLED */
  private String status;

  /** 创建时间 (ISO-8601) */
  private String createdAt;

  /** 更新时间 (ISO-8601) */
  private String updatedAt;
}
