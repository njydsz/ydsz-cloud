package com.njydsz.system.domain.query;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 特性开关管理分页查询参数（ydsz-system-domain）。
 *
 * @author ydsz-team
 * @since 26.10.09
 */
@Data
public class FeatureFlagPageQuery implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 页码（从 1 开始） */
  @Min(value = 1, message = "页码最小为 1")
  private Integer pageNum = 1;

  /** 每页条数（1-100） */
  @Min(value = 1, message = "每页条数最小为 1")
  @Max(value = 100, message = "每页条数最大为 100")
  private Integer pageSize = 20;

  /** 开关键模糊搜索（可选） */
  private String flagKey;

  /** 开关名称模糊搜索（可选） */
  private String flagName;

  /** 开关类型精确匹配（可选） */
  private String flagType;

  /** 启用状态精确匹配（可选） */
  private String status;
}
