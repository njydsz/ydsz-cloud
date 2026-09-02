package com.njydsz.literule.server.engine.liteexpr.BytecodeCompiler;

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
  private final ArrayList<Byte> bytecode = new ArrayList<>();

  /** 常量池 */
  private final List<Object> constantPool = new ArrayList<>(4);

  /** 源表达式文本 */
  private final String sourceExpression;

  /**
   * 创建字节码编译器
   *
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
    // 重置编译状态（编译器实例可复用，但每次编译必须清空上次状态）
    bytecode.clear();
    constantPool.clear();
    root.accept(this);
    emit(BytecodeOpcode.RETURN);
    return new CompiledProgram(toByteArray(bytecode), List.copyOf(constantPool), sourceExpression);
  }

  @Override
  public Void visitLiteral(LiteralNode node) {
    if (node.value() == null) {
      emit(BytecodeOpcode.LOAD_NULL);
    } else {
      int constIndex = addToConstantPool(node.value());
      emit(BytecodeOpcode.LOAD_CONST);
      emitU16(constIndex);
    }
    return null;
  }

  @Override
  public Void visitVariable(VariableNode node) {
    int nameIndex = addToConstantPool(new VarName(node.name()));
    emit(BytecodeOpcode.LOAD_VAR);
    emitU16(nameIndex);
    return null;
  }

  @Override
  public Void visitBinaryOp(BinaryOpNode node) {
    if ("&&".equals(node.operator()) || "and".equals(node.operator())) {
      // 短路求值：DUP 复制左操作数结果，为 false 时弹出测试值并跳转到末尾（结果留在栈顶）
      //   eval(left); DUP; JUMP_IF_FALSE end; eval(right); end:
      // left=false → 栈顶即 false；left=true → 继续求值 right，栈顶为最终结果
      node.left().accept(this);
      emit(BytecodeOpcode.DUP);
      int jumpToEnd = emitJump(BytecodeOpcode.JUMP_IF_FALSE);
      node.right().accept(this);
      patchJump(jumpToEnd);
    } else if ("||".equals(node.operator()) || "or".equals(node.operator())) {
      // 短路求值：DUP 复制左操作数结果，为 true 时弹出测试值跳到末尾，否则 POP 清理副本后求值右操作数
      //   eval(left); DUP; JUMP_IF_TRUE end; POP; eval(right); end:
      node.left().accept(this);
      emit(BytecodeOpcode.DUP);
      int jumpToEnd = emitJump(BytecodeOpcode.JUMP_IF_TRUE);
      emit(BytecodeOpcode.POP);
      node.right().accept(this);
      patchJump(jumpToEnd);
    } else {
      // 普通二元运算：先编译左操作数，再编译右操作数，最后发出运算指令
      node.left().accept(this);
      node.right().accept(this);
      BytecodeOpcode opcode = switch (node.operator()) {
        case "+" -> BytecodeOpcode.ADD;
        case "-" -> BytecodeOpcode.SUB;
        case "*" -> BytecodeOpcode.MUL;
        case "/" -> BytecodeOpcode.DIV;
        case "%" -> BytecodeOpcode.MOD;
        case ">" -> BytecodeOpcode.CMP_GT;
        case ">=" -> BytecodeOpcode.CMP_GE;
        case "<" -> BytecodeOpcode.CMP_LT;
        case "<=" -> BytecodeOpcode.CMP_LE;
        case "==" -> BytecodeOpcode.CMP_EQ;
        case "!=" -> BytecodeOpcode.CMP_NE;
        default -> throw new LiteExprException(
            "不支持的二元运算符: " + node.operator(), node.line(), node.column());
      };
      emit(opcode);
    }
    return null;
  }

  @Override
  public Void visitUnaryOp(UnaryOpNode node) {
    node.operand().accept(this);
    BytecodeOpcode opcode = switch (node.operator()) {
      case "!" -> BytecodeOpcode.LOGIC_NOT;
      case "-" -> BytecodeOpcode.NEG;
      default -> throw new LiteExprException(
          "不支持的一元运算符: " + node.operator(), node.line(), node.column());
    };
    emit(opcode);
    return null;
  }

  @Override
  public Void visitTernary(TernaryNode node) {
    // condition ? thenExpr : elseExpr
    // 编译为：
    //   evaluate condition
    //   JUMP_IF_FALSE elseLabel
    //   evaluate thenExpr
    //   JUMP endLabel
    // elseLabel:
    //   evaluate elseExpr
    // endLabel:
    node.condition().accept(this);
    int jumpToElse = emitJump(BytecodeOpcode.JUMP_IF_FALSE);
    node.thenExpr().accept(this);
    int jumpToEnd = emitJump(BytecodeOpcode.JUMP);
    patchJump(jumpToElse);
    node.elseExpr().accept(this);
    patchJump(jumpToEnd);
    return null;
  }

  @Override
  public Void visitFunctionCall(FunctionCallNode node) {
    // 参数压栈（从左到右）
    for (ExprNode arg : node.arguments()) {
      arg.accept(this);
    }
    int nameIndex = addToConstantPool(new VarName(node.functionName()));
    emit(BytecodeOpcode.CALL_FUNCTION);
    emitU16(nameIndex);
    emitU8(node.arguments().size());
    return null;
  }

  @Override
  public Void visitMemberAccess(MemberAccessNode node) {
    // obj.member → GET_MEMBER
    node.target().accept(this);
    int nameIndex = addToConstantPool(new VarName(node.member()));
    emit(BytecodeOpcode.GET_MEMBER);
    emitU16(nameIndex);
    return null;
  }

  @Override
  public Void visitIndex(IndexNode node) {
    node.target().accept(this);
    node.index().accept(this);
    emit(BytecodeOpcode.GET_INDEX);
    return null;
  }

  @Override
  public Void visitList(ListNode node) {
    // 列表在字节码层面不支持直接构造，降级为运行时异常
    throw new LiteExprException("字节码编译暂不支持列表字面量", node.line(), node.column());
  }

  @Override
  public Void visitMap(MapNode node) {
    throw new LiteExprException("字节码编译不支持 Map 字面量", node.line(), node.column());
  }

  @Override
  public Void visitLambda(LambdaNode node) {
    throw new LiteExprException("字节码编译不支持 Lambda 表达式", node.line(), node.column());
  }

  @Override
  public Void visitTemplateString(TemplateStringNode node) {
    throw new LiteExprException("字节码编译不支持模板字符串", node.line(), node.column());
  }

  // ===== 字节码发射工具方法 =====

  /** 发射单字节操作码 */
  private void emit(BytecodeOpcode opcode) {
    bytecode.add((byte) opcode.code());
  }

  /** 发射 u16 操作数（大端序） */
  private void emitU16(int value) {
    bytecode.add((byte) ((value >> BYTE_SHIFT) & BYTE_MASK));
    bytecode.add((byte) (value & BYTE_MASK));
  }

  /** 发射 u8 操作数 */
  private void emitU8(int value) {
    bytecode.add((byte) (value & BYTE_MASK));
  }

  /** 发射跳转指令并返回操作数位置（用于后续回填） */
  private int emitJump(BytecodeOpcode jumpOpcode) {
    emit(jumpOpcode);
    int operandPos = bytecode.size();
    emitU16(0);
    return operandPos;
  }

  /** 回填跳转目标（相对跳转，相对于操作数位置之后） */
  private void patchJump(int operandPos) {
    int currentPos = bytecode.size();
    int offset = currentPos - (operandPos + 2);
    bytecode.set(operandPos, (byte) ((offset >> BYTE_SHIFT) & BYTE_MASK));
    bytecode.set(operandPos + 1, (byte) (offset & BYTE_MASK));
  }

  /**
   * 添加值到常量池
   *
   * @param value 常量值
   * @return 常量池索引
   */
  private int addToConstantPool(Object value) {
    int index = constantPool.size();
    constantPool.add(value);
    return index;
  }

  /** 将字节码列表转换为紧凑 byte 数组 */
  private static byte[] toByteArray(List<Byte> list) {
    byte[] result = new byte[list.size()];
    for (int i = 0; i < list.size(); i++) {
      result[i] = list.get(i);
    }
    return result;
  }

  /**
   * 变量名标记类型 — 用于区分常量池中的 VarName 和普通字面量
   *
   * @param name 变量名或函数名
   */
  record VarName(String name) {
    @Override
    public String toString() {
      return "var:" + name;
    }
  }
}
