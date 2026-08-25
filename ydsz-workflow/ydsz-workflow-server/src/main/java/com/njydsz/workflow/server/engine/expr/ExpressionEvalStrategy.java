package com.njydsz.workflow.server.engine.expr;

/**
 * 表达式引擎策略枚举（对标 warm-flow ListenerStrategy）。
 *
 * <p>定义工作流引擎支持的表达式求值策略，业务方可通过配置 {@code ydsz.flow.expr-strategy} 动态切换。
 *
 * <p><b>内置策略：</b>
 *
 * <ul>
 *   <li>{@link #AVIATOR} — Aviator 表达式引擎（默认，轻量高效）
 *   <li>{@link #SPEL} — Spring SpEL 表达式引擎（与 Spring 生态深度集成）
 * </ul>
 *
 * <p><b>扩展方式：</b>业务方实现 {@link ExpressionEvaluator} 接口并注册为 Spring Bean，
 * 通过 {@link ExpressionEvaluatorRegistry} 自动发现。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExpressionEvaluator 表达式求值器接口
 * @see ExpressionEvaluatorRegistry 表达式引擎注册表
 */
public enum ExpressionEvalStrategy {

  /**
   * Aviator 表达式引擎（默认）。
   *
   * <p>轻量级高性能表达式引擎，支持大部分 Java 语法子集。
   * 适合条件表达式、办理人表达式等场景。安全加固版（禁用反射）。
   */
  AVIATOR("aviator", "Aviator 表达式引擎"),

  /**
   * Spring SpEL 表达式引擎。
   *
   * <p>Spring 官方表达式语言，与 Spring 生态深度集成，
   * 支持 Bean 引用、方法调用等高级特性。
   */
  SPEL("spel", "Spring SpEL 表达式引擎");

  /** 策略标识（配置文件中使用的值） */
  private final String code;

  /** 策略显示名称 */
  private final String name;

  ExpressionEvalStrategy(String code, String name) {
    this.code = code;
    this.name = name;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  /**
   * 根据 code 解析策略枚举。
   *
   * @param code 策略标识
   * @return 对应枚举，未匹配时返回 {@code null}
   */
  public static ExpressionEvalStrategy fromCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    for (ExpressionEvalStrategy s : values()) {
      if (s.code.equalsIgnoreCase(code)) {
        return s;
      }
    }
    return null;
  }
}
