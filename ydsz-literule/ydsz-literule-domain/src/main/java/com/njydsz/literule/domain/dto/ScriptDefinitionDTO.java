package com.njydsz.literule.domain.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.njydsz.literule.domain.Rule;

/**
 * 脚本规则定义（DTO）
 *
 * <p>基于 LiteExpr 的动态脚本规则，适用于表达式无法覆盖的复杂场景 （多步骤条件判断、循环检查、复杂对象操作等）。
 *
 * <p>脚本约定：
 *
 * <ul>
 *   <li>通过 {@code facts} 变量访问事实数据（{@code Map<String, Object>}）
 *   <li>返回 boolean：true=触发，false=不触发
 *   <li>可设置 {@code severity} / {@code title} / {@code description} 变量自定义结果
 * </ul>
 *
 * <p>沙箱模式（默认启用）禁止 System.exit / Runtime.exec / 反射 / 文件 I/O / 网络访问等危险 API。
 *
 * <p>持久化于 {@code ydsz_rule_script}（见 V048，script 字段为 TEXT）， 由 {@code ScriptConfigProvider} SPI 加载，
 * 通过 {@code ScriptRule#from(ScriptDefinitionDTO)} 转换为可执行规则。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptDefinitionDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 规则编码（唯一） */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 类别（如 COMPLEX / GENERAL） */
  private String category;

  /** 描述 */
  private String description;

  /**
   * 脚本语言（1.5.0 起）
   *
   * <p>可选值：
   *
   * <ul>
   *   <li>{@code groovy}（默认）- Groovy JSR-223，语法灵活
   *   <li>{@code javascript} / {@code js} - Nashorn JSR-223，ECMAScript 语法
   *   <li>{@code python} - Jython JSR-223，Python 2.7 语法（需引入 jython 依赖）
   * </ul>
   */
  @Builder.Default private String language = "groovy";

  /** 脚本内容 */
  private String script;

  /** 默认严重度字符串（"RED"/"YELLOW"/"INFO"，脚本未设置 severity 时使用） */
  @Builder.Default private String defaultSeverity = "INFO";

  /** 是否启用沙箱（默认 TRUE） */
  @Builder.Default private boolean sandboxEnabled = true;

  /** 是否启用 */
  @Builder.Default private boolean enabled = true;

  /** 优先级（数值越小越先执行） */
  @Builder.Default private int priority = Rule.DEFAULT_PRIORITY;

  /** 影响范围（用于场景过滤） */
  private String scope;

  /** 当前版本号 */
  @Builder.Default private int version = 1;
}
