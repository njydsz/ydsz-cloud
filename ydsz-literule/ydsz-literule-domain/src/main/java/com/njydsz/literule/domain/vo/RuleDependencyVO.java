package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 规则依赖关系视图对象（VO）。
 *
 * <p>用于 Controller 层返回规则之间的依赖关系信息，包含依赖类型、级联禁用配置 及描述，支撑规则拓扑分析和依赖影响评估。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RuleDependencyVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 依赖记录唯一标识（主键） */
  private String id;

  /** 规则编码 */
  private String ruleCode;

  /** 被依赖的规则编码 */
  private String dependsOnRuleCode;

  /** 依赖类型（HARD/SOFT/TRIGGER） */
  private String dependencyType;

  /** 禁用被依赖规则时是否级联禁用本规则 */
  private Boolean cascadeOnDisable;

  /** 依赖描述 */
  private String description;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
