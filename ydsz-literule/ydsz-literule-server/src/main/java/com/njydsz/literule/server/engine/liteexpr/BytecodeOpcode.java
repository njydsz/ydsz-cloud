package com.njydsz.literule.server.engine.liteexpr;

/**
 * LiteExpr 字节码指令集
 *
 * <p>栈式虚拟机的操作码定义，设计原则：
 *
 * <ul>
 *   <li>操作码分类：常量加载、变量访问、算术/比较/逻辑运算、控制流、函数调用、数据访问
 *   <li>栈效应明确：每个操作码对操作数栈的影响在注释中说明（+push/-pop）
 *   <li>单字节编码：所有操作码可用 byte 表示，紧凑存储
 * </ul>
 *
 * <p>栈效应标注约定：{@code →} 前面为操作前栈，后面为操作后栈。 例如 {@code [a, b] → [result]} 表示弹出 a、b，压入 result。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public enum BytecodeOpcode {

  // ===== 常量加载（→ value） =====

  /** 加载 null 值到栈顶。栈效应：→ [null] */
  LOAD_NULL(0x01),

  /** 从常量池加载常量到栈顶。操作数：u16 constantIndex。栈效应：→ [value] */
  LOAD_CONST(0x02),

  // ===== 变量访问 =====

  /** 加载变量值到栈顶。操作数：u16 nameIndex（变量名在常量池中的索引）。栈效应：→ [value] */
  LOAD_VAR(0x10),

  // ===== 算术运算（[a, b] → [result]） =====

  /** 二元加法。栈效应：[a, b] → [a + b] */
  ADD(0x20),

  /** 二元减法。栈效应：[a, b] → [a - b] */
  SUB(0x21),

  /** 二元乘法。栈效应：[a, b] → [a * b] */
  MUL(0x22),

  /** 二元除法。栈效应：[a, b] → [a / b] */
  DIV(0x23),

  /** 二元取模。栈效应：[a, b] → [a % b] */
  MOD(0x24),

  /** 一元取负。栈效应：[a] → [-a] */
  NEG(0x25),

  /** 栈顶复制。栈效应：[a] → [a, a]，用于短路求值保留操作数结果 */
  DUP(0x26),

  /** 弹出并丢弃栈顶值。栈效应：[a] →，用于短路求值的栈平衡 */
  POP(0x27),

  // ===== 比较运算（[a, b] → [boolean]） =====

  /** 大于。栈效应：[a, b] → [a > b] */
  CMP_GT(0x30),

  /** 大于等于。栈效应：[a, b] → [a >= b] */
  CMP_GE(0x31),

  /** 小于。栈效应：[a, b] → [a < b] */
  CMP_LT(0x32),

  /** 小于等于。栈效应：[a, b] → [a <= b] */
  CMP_LE(0x33),

  /** 等于。栈效应：[a, b] → [a == b] */
  CMP_EQ(0x34),

  /** 不等于。栈效应：[a, b] → [a != b] */
  CMP_NE(0x35),

  // ===== 逻辑运算 =====

  /** 逻辑与（短路）。栈效应：[a, b] → [a && b]，编译为条件跳转实现短路 */
  LOGIC_AND(0x40),

  /** 逻辑或（短路）。栈效应：[a, b] → [a || b]，编译为条件跳转实现短路 */
  LOGIC_OR(0x41),

  /** 逻辑非。栈效应：[a] → [!a] */
  LOGIC_NOT(0x42),

  // ===== 控制流 =====

  /** 无条件跳转。操作数：u16 targetOffset。栈效应：→（不变） */
  JUMP(0x50),

  /**
   * 条件为 false 时跳转。操作数：u16 targetOffset。栈效应：[cond] → （弹出测试值）。
   *
   * <p>测试值为 false 时跳转到目标偏移；无论是否跳转，测试值均已从栈中弹出。
   * 短路求值场景须先用 {@link #DUP} 复制操作数以保留最终结果。
   */
  JUMP_IF_FALSE(0x51),

  /** 条件为 true 时跳转。操作数：u16 targetOffset。栈效应：[cond] → （弹出测试值，语义同 {@link #JUMP_IF_FALSE}） */
  JUMP_IF_TRUE(0x52),

  /** 条件为 null 时跳转（空值安全短路）。操作数：u16 targetOffset。栈效应：[ref] → */
  JUMP_IF_NULL(0x53),

  // ===== 函数调用 =====

  /** 调用函数。操作数：u16 nameIndex, u8 argCount。栈效应：[argN, ..., arg1, arg0] → [result] */
  CALL_FUNCTION(0x60),

  // ===== 数据访问 =====

  /** 属性访问。栈效应：[obj, name] → [obj.name]；obj 为 null 时结果为 null（空值安全） */
  GET_MEMBER(0x70),

  /** 索引访问。栈效应：[coll, key] → [coll[key]] */
  GET_INDEX(0x71),

  // ===== 程序控制 =====

  /** 返回栈顶值作为结果。栈效应：[result] → */
  RETURN(0xFF);

  /** 操作码字节编码 */
  private final int code;

  BytecodeOpcode(int code) {
    this.code = code;
  }

  /**
   * 获取操作码字节值
   *
   * @return 字节编码
   */
  public int code() {
    return code;
  }

  /**
   * 从字节值查找操作码
   *
   * @param code 字节编码
   * @return 对应的操作码
   * @throws IllegalArgumentException 未知操作码
   */
  public static BytecodeOpcode fromCode(int code) {
    for (BytecodeOpcode op : values()) {
      if (op.code == code) {
        return op;
      }
    }
    throw new IllegalArgumentException("未知字节码操作码: 0x" + Integer.toHexString(code));
  }
}
