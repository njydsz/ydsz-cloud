package com.njydsz.literule.server.engine.liteexpr;
import java.util.List;

/**
 * LiteExpr 字节码编译器 — 将 AST 编译为栈式虚拟机字节码
 *
 * <p>遍历 {@link ExprNode} AST，输出 {@link BytecodeOpcode} 指令序列到 {@link CompiledProgram}。
 * 编译过程采用经典的"单遍遍历 + 回填"策略：
 *
 * <ul>
 *   <li>字面量和变量：直接发出 LOAD 指令
 *   <li>二元/一元运算：递归编译操作数后发出运算指令
 *   <li>逻辑运算（AND/OR）：使用条件跳转实现短路求值
 *   <li>三元条件：编译为 JUMP_IF_FALSE + JUMP 控制流
 *   <li>函数调用：压栈参数后发出 CALL_FUNCTION
 *   <li>属性访问：发出 GET_MEMBER（空值安全由 VM 运行时保证）
 * </ul>
 *
 * <p>字节码布局：
 *
 * <pre>
 * [opcode byte] [operand1 byte] [operand2 byte] ...
 * </pre>
 *
 * <p>操作数编码：u16（2 字节，大端），u8（1 字节）。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public class BytecodeCompiler implements ExprNodeVisitor<Void> {

  /** 字节掩码（无符号字节取值 0~255） */
  static final int BYTE_MASK = 0xFF;

  /** 字节位移位数（u16 高字节） */
  static final int BYTE_SHIFT = 8;

  /** 字节码输出缓冲区 */
  private final ArrayList<Byte> bytecode = new ArrayList<>(4);
}
