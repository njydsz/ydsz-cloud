package com.njydsz.literule.server.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.server.impl.ExpressionRule;

/**
 * LiteRule SDK 入口 —— 面向 Java 开发者的极简 API
 *
 * <p>提供链式 Builder 构建规则、一行代码评估的极简体验。 适用于嵌入式场景（不依赖 Spring）和 Spring Boot 场景（通过 AutoConfiguration
 * 自动注入）。
 *
 * <h3>快速入门（嵌入式）</h3>
 *
 * <pre>{@code
 * LiteRuleSdk sdk = LiteRuleSdk.builder()
 *     .tenantId("T001")
 *     .environment("prod")
 *     .build();
 *
 * // 编程式注册规则
 * sdk.addRule(RuleDefinitionDTO.builder()
 *     .code("R001")
 *     .name("高额预警")
 *     .conditionExpression("amount > 10000")
 *     .defaultSeverity(RuleSeverity.RED)
 *     .build());
 *
 * // 评估
 * List<RuleResultVO> results = sdk.evaluate(Map.of("amount", 15000));
 * }</pre>
 *
 * <h3>链式 Builder 注册规则</h3>
 *
 * <pre>{@code
 * sdk.rule("R002")
 *     .name("示例告警规则")
 *     .condition("metricValue > 0.05 && flagEnabled > 0")
 *     .severity(RuleSeverity.YELLOW)
 *     .priority(10)
 *     .register();
 * }</pre>
 *
 * <p>注意：本类与 {@code com.njydsz.literule.api.client.LiteRuleClient}（Feign 远程调用接口） 是不同的概念。{@code
 * LiteRuleSdk} 是嵌入式 SDK 入口，{@code LiteRuleClient} 是远程 Feign 客户端。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class LiteRuleSdk {

  private final RuleEngine ruleEngine;
  private final ExpressionEngine evaluator;
  private final String tenantId;
  private final String environment;
  private final Map<String, RuleDefinitionDTO> ruleDefinitions = new ConcurrentHashMap<>();

  public LiteRuleSdk(
      RuleEngine ruleEngine, ExpressionEngine evaluator, String tenantId, String environment) {
    this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine");
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    this.tenantId = tenantId != null ? tenantId : "1";
    this.environment = environment != null ? environment : "default";
  }

  /**
   * 创建 SDK 构建器
   *
   * @return LiteRuleSdk 构建器实例
   */
  public static LiteRuleSdkBuilder builder() {
    return new LiteRuleSdkBuilder();
  }

  /**
   * 编程式注册规则定义
   *
   * @param definition 规则定义
   */
  public void addRule(RuleDefinitionDTO definition) {
    Objects.requireNonNull(definition, "definition");
    Objects.requireNonNull(definition.getCode(), "rule code");

    // 填充租户和环境（如果未设置）
    if (definition.getTenantId() == null || definition.getTenantId().isBlank()) {
      definition.setTenantId(tenantId);
    }
    if (definition.getEnvironment() == null || definition.getEnvironment().isBlank()) {
      definition.setEnvironment(environment);
    }

    ExpressionRule rule = new ExpressionRule(definition, evaluator);
    ruleEngine.register(rule);
    ruleDefinitions.put(definition.getCode(), definition);
  }

  /**
   * 移除规则
   *
   * @param ruleCode 规则编码
   */
  public void removeRule(String ruleCode) {
    ruleEngine.unregister(ruleCode);
    ruleDefinitions.remove(ruleCode);
  }

  /**
   * 评估规则（使用默认租户和环境）
   *
   * @param facts 事实数据
   * @return 触发的规则结果列表
   */
  public List<RuleResultVO> evaluate(Map<String, Object> facts) {
    return evaluate(facts, null);
  }

  /**
   * 获取已注册的规则数量
   *
   * @return 当前已注册的规则定义总数
   */
  public int ruleCount() {
    return ruleDefinitions.size();
  }

  /**
   * 评估规则（指定场景）
   *
   * @param facts 事实数据
   * @param scenario 业务场景标识
   * @return 触发的规则结果列表
   */
  public List<RuleResultVO> evaluate(Map<String, Object> facts, String scenario) {
    String scen = scenario != null ? scenario : "DEFAULT";
    RuleContextVO context = RuleContextVO.of(facts, scen, "SDK", null, tenantId, environment);
    return ruleEngine.evaluate(context);
  }

  /**
   * Dry-run 仿真（返回全部结果含未触发）
   *
   * @param facts 事实数据
   * @return 全部规则结果
   */
  public List<RuleResultVO> dryRun(Map<String, Object> facts) {
    RuleContextVO context = RuleContextVO.of(facts, "DRY_RUN", "SDK", null, tenantId, environment);
    return ruleEngine.dryRun(context);
  }

  /**
   * 获取最高严重度结果
   *
   * @param facts 事实数据
   * @return 最高严重度结果；无触发返回 null
   */
  public RuleResultVO topResult(Map<String, Object> facts) {
    RuleContextVO context = RuleContextVO.of(facts, "TOP", "SDK", null, tenantId, environment);
    return ruleEngine.topResult(context);
  }

  /**
   * 获取已注册的规则定义列表
   *
   * @return 当前已注册的规则定义列表（防御性拷贝）
   */
  public List<RuleDefinitionDTO> getRuleDefinitions() {
    return new ArrayList<>(ruleDefinitions.values());
  }

  /**
   * 链式创建规则 Builder
   *
   * @param code 规则编码
   * @return 链式 Builder
   */
  public RuleBuilder rule(String code) {
    return new RuleBuilder(this, code);
  }

  /**
   * 获取底层 RuleEngine（高级用法）
   *
   * @return 当前 SDK 使用的规则引擎实例
   */
  public RuleEngine getEngine() {
    return ruleEngine;
  }

  // ==================== RuleBuilder ====================

  /** 链式规则构建器 */
  public static class RuleBuilder {
    private final LiteRuleSdk sdk;
    private final RuleDefinitionDTO.RuleDefinitionBuilder builder;

    RuleBuilder(LiteRuleSdk sdk, String code) {
      this.sdk = sdk;
      this.builder = RuleDefinitionDTO.builder().code(code);
    }

    /**
     * 设置规则展示名称（仅用于 UI 展示，可空）。
     *
     * @param name 规则名称
     * @return 当前 Builder（链式调用）
     */
    public RuleBuilder name(String name) {
      builder.name(name);
      return this;
    }

    /**
     * 设置规则分类（category_path，用于目录树归类与按路径过滤）。
     *
     * @param category 分类路径（如 {@code "demo/group"}），可空
     * @return 当前 Builder（链式调用）
     */
    public RuleBuilder category(String category) {
      builder.category(category);
      return this;
    }

    /**
     * 设置规则描述（业务说明，仅展示用，可空）。
     *
     * @param desc 规则描述
     * @return 当前 Builder（链式调用）
     */
    public RuleBuilder description(String desc) {
      builder.description(desc);
      return this;
    }

    /**
     * 设置条件表达式（规则的核心判定逻辑，映射到 RuleDefinitionDTO 的 conditionExpression）。
     *
     * <p>表达式为空或非法将导致规则无法正确触发。建议在 {@link #register()} 前务必设置， 否则评估时按空条件处理（通常视为不触发）。
     *
     * @param expression LiteExpr 条件表达式
     * @return 当前 Builder（链式调用）
     */
    public RuleBuilder condition(String expression) {
      builder.conditionExpression(expression);
      return this;
    }

    /**
     * 设置默认严重度（规则触发时上报的严重级别；为 null 时由引擎使用默认级别）。
     *
     * @param severity 严重度枚举（{@code RED}/{@code YELLOW}/{@code INFO} 等）
     * @return 当前 Builder（链式调用）
     */
    public RuleBuilder severity(RuleSeverity severity) {
      builder.defaultSeverity(severity);
      return this;
    }

    /**
     * 设置规则优先级（数值越大优先级越高，用于并行/冲突场景的排序与冲突裁决）。
     *
     * @param priority 优先级数值
     * @return 当前 Builder（链式调用）
     */
    public RuleBuilder priority(int priority) {
      builder.priority(priority);
      return this;
    }

    /**
     * 设置规则是否启用（false 时注册后不参与评估，仅作占位/草稿）。
     *
     * @param enabled true=启用；false=禁用
     * @return 当前 Builder（链式调用）
     */
    public RuleBuilder enabled(boolean enabled) {
      builder.enabled(enabled);
      return this;
    }

    /**
     * 设置告警标题模板（支持占位符，触发时渲染为通知标题）。
     *
     * @param template 标题模板字符串，可空
     * @return 当前 Builder（链式调用）
     */
    public RuleBuilder titleTemplate(String template) {
      builder.titleTemplate(template);
      return this;
    }

    /**
     * 设置告警描述模板（支持占位符，触发时渲染为通知正文）。
     *
     * @param template 描述模板字符串，可空
     * @return 当前 Builder（链式调用）
     */
    public RuleBuilder descriptionTemplate(String template) {
      builder.descriptionTemplate(template);
      return this;
    }

    /** 完成构建并注册到客户端 */
    public void register() {
      sdk.addRule(builder.build());
    }
  }
}
