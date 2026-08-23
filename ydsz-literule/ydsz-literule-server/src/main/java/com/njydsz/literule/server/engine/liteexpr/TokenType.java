package com.njydsz.literule.server.engine.liteexpr;

/**
 * LiteExpr 表达式引擎 — Token 类型枚举
 *
 * <p>定义自研表达式引擎的所有词法单元类型。Lexer 将源代码拆分为 {@link Token} 序列，每个 Token 携带一个 {@link TokenType}。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public enum TokenType {

  // ===== 字面量 =====
  /** 整数字面量（如 42 / 0xFF / 100L） */
  INTEGER,
  /** 浮点字面量（如 3.14 / 1e-5 / 2.5BD） */
  DECIMAL,
  /** 字符串字面量（"..." / '...' / `...`） */
  STRING,
  /** 布尔字面量 true / false */
  BOOLEAN,
  /** null 字面量 */
  NULL,

  // ===== 标识符 =====
  /** 标识符（变量名、函数名，含 Unicode 中文） */
  IDENTIFIER,

  // ===== 运算符 =====
  /** + */
  PLUS, // +
  /** - */
  MINUS, // -
  /** 乘号 */
  STAR, // *
  /** / */
  SLASH, // /
  /** % */
  PERCENT, // %
  /** == */
  EQ, // ==
  /** != */
  NEQ, // !=
  /** > */
  GT, // >
  /** >= */
  GTE, // >=
  /** < */
  LT, // <
  /** <= */
  LTE, // <=
  /** && 或 and */
  AND, // && 或 and
  /** || 或 or */
  OR, // || 或 or
  /** ! 或 not */
  NOT, // ! 或 not

  // ===== 分隔符 =====
  /** ( */
  LPAREN, // (
  /** ) */
  RPAREN, // )
  /** [ */
  LBRACKET, // [
  /** ] */
  RBRACKET, // ]
  /** { */
  LBRACE, // {
  /** } */
  RBRACE, // }
  /** , */
  COMMA, // ,
  /** . */
  DOT, // .
  /** : */
  COLON, // :
  /** ? */
  QUESTION, // ?
  /** -> */
  ARROW, // ->

  // ===== 特殊 =====
  /** 字符串模板片段（反引号内的非变量部分，如 `Hello `） */
  TEMPLATE_STR,
  /** 字符串模板变量开始 ${ */
  TEMPLATE_VAR_START,
  /** 字符串模板变量结束 } */
  TEMPLATE_VAR_END,
  /** 源代码结束 */
  EOF;

  /** 运算符优先级：逻辑或 */
  private static final int PREC_OR = 20;

  /** 运算符优先级：逻辑与 */
  private static final int PREC_AND = 30;

  /** 运算符优先级：相等比较 */
  private static final int PREC_EQUALITY = 40;

  /** 运算符优先级：关系比较 */
  private static final int PREC_RELATIONAL = 50;

  /** 运算符优先级：加减 */
  private static final int PREC_ADDITIVE = 60;

  /** 运算符优先级：乘除取模 */
  private static final int PREC_MULTIPLICATIVE = 70;

  /**
   * 判断当前 Token 是否为二元运算符
   *
   * @return true=二元运算符
   */
  public boolean isBinaryOperator() {
    return switch (this) {
      case PLUS, MINUS, STAR, SLASH, PERCENT, EQ, NEQ, GT, GTE, LT, LTE, AND, OR -> true;
      default -> false;
    };
  }

  /**
   * 获取运算符优先级（数值越大优先级越高）
   *
   * @return 优先级；非运算符返回 0
   */
  public int precedence() {
    return switch (this) {
      case OR -> PREC_OR;
      case AND -> PREC_AND;
      case EQ, NEQ -> PREC_EQUALITY;
      case GT, GTE, LT, LTE -> PREC_RELATIONAL;
      case PLUS, MINUS -> PREC_ADDITIVE;
      case STAR, SLASH, PERCENT -> PREC_MULTIPLICATIVE;
      default -> 0;
    };
  }
}
