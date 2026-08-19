package com.njydsz.literule.server.engine.liteexpr;

/**
 * LiteExpr 表达式求值器（旧类名，A3 命名修正兼容别名）
 *
 * <p><b>已废弃</b>：本类真实实现为自研 LiteExpr 引擎，与 Aviator 无任何关系， 旧类名严重误导。请使用 {@link LiteExprEngine}。
 * 本类保留 1-2 个版本作为兼容别名（继承 {@link LiteExprEngine}，行为完全一致）， 之后将移除。
 *
 * @deprecated 自 1.0.0 起使用 {@link LiteExprEngine} 替代
 * @since 1.0.0
 * @author ydsz-team
 */
@Deprecated
public class AviatorExpressionEngine extends LiteExprEngine {

  /** 构造（默认启用沙箱） */
  public AviatorExpressionEngine() {
    super();
  }

  /**
   * 构造
   *
   * @param sandboxEnabled 是否启用 AST 级沙箱
   */
  public AviatorExpressionEngine(boolean sandboxEnabled) {
    super(sandboxEnabled);
  }
}
