package com.njydsz.literule.server.orchestrator;

/**
 * 规则链类型枚举
 *
 * <p>定义规则编排的五种核心语义：
 *
 * <ul>
 *   <li>{@link #THEN} - 顺序执行：节点依次串行执行，收集触发结果
 *   <li>{@link #WHEN} - 并行执行：节点并发执行（基于 CompletableFuture），收集触发结果
 *   <li>{@link #IF} - 条件执行：先对条件表达式求值，为 true 才执行动作规则
 *   <li>{@link #ELIF} - 多分支条件：依次求值多个条件，执行第一个匹配的分支
 *   <li>{@link #SWITCH} - 分支选择：从上下文中取分支 key，执行对应分支规则
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public enum RuleChainType {

  /** 顺序执行 */
  THEN("顺序"),

  /** 并行执行 */
  WHEN("并行"),

  /** 条件执行 */
  IF("条件"),

  /** 多分支条件 */
  ELIF("多分支"),

  /** 分支选择 */
  SWITCH("分支");

  /** 类型描述（中文） */
  private final String desc;

  RuleChainType(String desc) {
    this.desc = desc;
  }

  /**
   * 获取类型描述
   *
   * @return 描述文本
   */
  public String getDesc() {
    return desc;
  }
}
