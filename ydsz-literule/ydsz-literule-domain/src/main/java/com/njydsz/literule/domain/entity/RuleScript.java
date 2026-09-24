package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 规则脚本实体。
 *
 * <p>对应 {@code ydsz_rule_script} 表，以 Groovy 脚本作为规则条件与动作的执行载体，
 * 通过 {@code script} 字段存储脚本源码。脚本运行时通过 Aviator / Groovy Shell 引擎求值，
 * 可使用 {@code isSandboxEnabled} 开关控制是否在安全沙箱中执行，防止恶意代码访问系统资源。
 *
 * <p>适用于表达式引擎无法满足的复杂业务逻辑场景，如多表关联查询、递归计算、自定义聚合等。
 * 优先级（{@code priority}）与其他规则类型统一排序，数字越小越先执行。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @Data 与 JPA 继承共用，父类字段泛型擦除
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ydsz_rule_script", autoResultMap = true)
public class RuleScript extends MpBaseEntity<String> {

  /** 规则编码 */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 规则分类 */
  private String category;

  /** 规则描述 */
  private String description;

  /** Groovy 脚本源码 */
  private String script;

  /** 默认严重级别：INFO/WARN/ERROR/CRITICAL */
  private String defaultSeverity;

  /** 是否启用沙箱 */
  private Boolean isSandboxEnabled;

  /** 优先级 */
  private Integer priority;

  /** 是否启用 */
  private Boolean isEnabled;

  /** 适用范围 */
  private String scope;

  /** 版本号 */
  private Integer version;

  /** 供应商侧追踪 ID */
  private String providerTraceId;
}
