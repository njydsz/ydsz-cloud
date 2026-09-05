package com.njydsz.literule.server.engine.liteexpr;

import java.util.ArrayList;
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
 * @since 26.09.01
 * @author ydsz-team
 */
public class BytecodeCompiler implements ExprNodeVisitor<Void> {

  /** 字节掩码（无符号字节取值 0~255） */
  static final int BYTE_MASK = 0xFF;

  /** 字节位移位数（u16 高字节） */
  static final int BYTE_SHIFT = 8;

  /** 字节码输出缓冲区 */
  private final ArrayList<Byte> bytecode = new ArrayList<>(64);

  /** 常量池 */
  private final ArrayList<Object> constantPool = new ArrayList<>(16);

  /** 源表达式文本 */
  private final String sourceExpression;

  /** 跳转补丁列表：存储待回填的跳转偏移位置 */
  private final ArrayList<Integer> patchList = new ArrayList<>(8);

  /**
   * @param sourceExpression 源表达式文本（调试用）
   */
  public BytecodeCompiler(String sourceExpression) {
    this.sourceExpression = sourceExpression;
  }

  /**
   * 编译 AST 为字节码程序
   *
   * @param root AST 根节点
   * @return 编译后的字节码程序
   */
  public CompiledProgram compile(ExprNode root) {
    bytecode.clear();
    constantPool.clear();
    patchList.clear();
    root.accept(this);
    emitOpcode(BytecodeOpcode.RETURN);
    return new CompiledProgram(toByteArray(bytecode), List.copyOf(constantPool), sourceExpression);
  }

  // ===== Visitor 实现 =====

  @Override
  public Void visitLiteral(LiteralNode node) {
    int constIndex = addToConstantPool(node.value());
    emitOpcode(BytecodeOpcode.LOAD_CONST);
    emitU16(constIndex);
    return null;
  }

  @Override
  public Void visitVariable(VariableNode node) {
    int nameIndex = addToConstantPool(node.name());
    emitOpcode(BytecodeOpcode.LOAD_VAR);
    emitU16(nameIndex);
    return null;
  }

  @Override
  public Void visitBinaryOp(BinaryOpNode node) {
    if (node.isLogical() && "&&".equals(node.operator())) {
      // 短路 AND：left 为 false 时跳过 right
      node.left().accept(this);
      emitOpcode(BytecodeOpcode.DUP); // 保留结果
      int jumpIfFalse = emitJump(BytecodeOpcode.JUMP_IF_FALSE);
      emitOpcode(BytecodeOpcode.POP); // 弹出 true 结果
      node.right().accept(this);
      patchJump(jumpIfFalse);
      return null;
    }
    if (node.isLogical() && "||".equals(node.operator())) {
      // 短路 OR：left 为 true 时跳过 right
      node.left().accept(this);
      emitOpcode(BytecodeOpcode.DUP);
      int jumpIfTrue = emitJump(BytecodeOpcode.JUMP_IF_TRUE);
      emitOpcode(BytecodeOpcode.POP);
      node.right().accept(this);
      patchJump(jumpIfTrue);
      return null;
    }
    // 普通二元运算
    node.left().accept(this);
    node.right().accept(this);
    emitOpcode(switch (node.operator()) {
      case "+", "plus" -> BytecodeOpcode.ADD;
      case "-", "minus" -> BytecodeOpcode.SUB;
      case "*", "multiply" -> BytecodeOpcode.MUL;
      case "/", "divide" -> BytecodeOpcode.DIV;
      case "%", "modulo" -> BytecodeOpcode.MOD;
      case ">" -> BytecodeOpcode.CMP_GT;
      case ">=" -> BytecodeOpcode.CMP_GE;
      case "<" -> BytecodeOpcode.CMP_LT;
      case "<=" -> BytecodeOpcode.CMP_LE;
      case "==" -> BytecodeOpcode.CMP_EQ;
      case "!=" -> BytecodeOpcode.CMP_NE;
      default -> throw new RuntimeException("未知运算符: " + node.operator());
    });
    return null;
  }

  @Override
  public Void visitUnaryOp(UnaryOpNode node) {
    node.operand().accept(this);
    switch (node.operator()) {
      case "!" -> emitOpcode(BytecodeOpcode.LOGIC_NOT);
      case "-" -> emitOpcode(BytecodeOpcode.NEG);
      default -> throw new RuntimeException("未知一元运算符: " + node.operator());
    }
    return null;
  }

  @Override
  public Void visitTernary(TernaryNode node) {
    node.condition().accept(this);
    int jumpIfFalse = emitJump(BytecodeOpcode.JUMP_IF_FALSE);
    node.thenExpr().accept(this);
    int jumpEnd = emitJump(BytecodeOpcode.JUMP);
    patchJump(jumpIfFalse);
    node.elseExpr().accept(this);
    patchJump(jumpEnd);
    return null;
  }

  @Override
  public Void visitFunctionCall(FunctionCallNode node) {
    // 压栈参数（正序）
    for (ExprNode arg : node.arguments()) {
      arg.accept(this);
    }
    int nameIndex = addToConstantPool(node.functionName());
    emitOpcode(BytecodeOpcode.CALL_FUNCTION);
    emitU16(nameIndex);
    emitByte(node.arguments().size());
    return null;
  }

  @Override
  public Void visitMemberAccess(MemberAccessNode node) {
    node.target().accept(this);
    int nameIndex = addToConstantPool(node.member());
    emitOpcode(BytecodeOpcode.GET_MEMBER);
    emitU16(nameIndex);
    return null;
  }

  @Override
  public Void visitIndex(IndexNode node) {
    node.target().accept(this);
    node.index().accept(this);
    emitOpcode(BytecodeOpcode.GET_INDEX);
    return null;
  }

  @Override
  public Void visitList(ListNode node) {
    // 列表编译为顺序压栈
    for (ExprNode element : node.elements()) {
      element.accept(this);
    }
    return null;
  }

  @Override
  public Void visitMap(MapNode node) {
    // Map 编译为顺序压栈 key-value
    node.entries().forEach((k, v) -> {
      k.accept(this);
      v.accept(this);
    });
    return null;
  }

  @Override
  public Void visitLambda(LambdaNode node) {
    // Lambda 编译为函数引用常量
    int constIndex = addToConstantPool("lambda:" + node.parameter() + "->" + node.body().exprText());
    emitOpcode(BytecodeOpcode.LOAD_CONST);
    emitU16(constIndex);
    return null;
  }

  @Override
  public Void visitTemplateString(TemplateStringNode node) {
    // 模板字符串编译为字符串拼接
    boolean first = true;
    for (ExprNode part : node.parts()) {
      part.accept(this);
      if (!first) {
        emitOpcode(BytecodeOpcode.ADD);
      }
      first = false;
    }
    if (node.parts().isEmpty()) {
      visitLiteral(new LiteralNode("", node.line(), node.column(), ""));
    }
    return null;
  }

  // ===== 字节码发射辅助方法 =====

  /**
   * 发出单字节操作码
   */
  private void emitOpcode(BytecodeOpcode opcode) {
    bytecode.add((byte) opcode.code());
  }

  /**
   * 发出单字节操作数
   */
  private void emitByte(int value) {
    bytecode.add((byte) (value & BYTE_MASK));
  }

  /**
   * 发出双字节操作数（u16 大端）
   */
  private void emitU16(int value) {
    bytecode.add((byte) ((value >> BYTE_SHIFT) & BYTE_MASK));
    bytecode.add((byte) (value & BYTE_MASK));
  }

  /**
   * 发出跳转指令，返回待补丁的偏移位置
   *
   * @return 跳转偏移的索引位置（需要后续 patchJump 回填）
   */
  private int emitJump(BytecodeOpcode jumpOpcode) {
    emitOpcode(jumpOpcode);
    int patchPos = bytecode.size();
    emitU16(0); // 占位
    return patchPos;
  }

  /**
   * 回填跳转偏移
   *
   * @param patchPos emitJump 返回的位置
   */
  private void patchJump(int patchPos) {
    int offset = bytecode.size() - patchPos - 2; // 减去 u16 自身长度
    bytecode.set(patchPos, (byte) ((offset >> BYTE_SHIFT) & BYTE_MASK));
    bytecode.set(patchPos + 1, (byte) (offset & BYTE_MASK));
  }

  /**
   * 添加常量到常量池，返回索引
   *
   * @param value 常量值
   * @return 常量池索引
   */
  private int addToConstantPool(Object value) {
    int index = constantPool.indexOf(value);
    if (index >= 0) {
      return index;
    }
    constantPool.add(value);
    return constantPool.size() - 1;
  }

  /**
   * 将 Byte 列表转换为 byte[]
   */
  private byte[] toByteArray(List<Byte> bytes) {
    byte[] result = new byte[bytes.size()];
    for (int i = 0; i < bytes.size(); i++) {
      result[i] = bytes.get(i);
    }
    return result;
  }
}
