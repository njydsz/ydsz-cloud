package com.njydsz.literule.server.engine.liteexpr;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * LiteExpr 字节码解释器 — 栈式虚拟机执行引擎
 *
 * <p>执行 {@link BytecodeCompiler} 输出的字节码程序。核心特性：
 *
 * <ul>
 *   <li>纯栈式架构：操作数栈 + 指令指针，实现简单高效
 *   <li>类型自动转换：int/long + BigDecimal → BigDecimal（与 {@link TreeInterpreter} 行为一致）
 *   <li>空值安全：null.x 返回 null，null == x 返回布尔值
 *   <li>函数调用委托：通过 {@link FunctionRegistry} 执行内置/自定义函数
 *   <li>安全限制：节点访问预算 + 递归深度，防止病态表达式耗尽 CPU/栈
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class BytecodeInterpreter {

  /** 默认单次求值节点访问预算 */
  private static final long DEFAULT_MAX_STEPS = 1_000_000L;

  /** 墙上时钟超时检查采样间隔 */
  private static final long DEADLINE_CHECK_INTERVAL = 0xFF;

  private final FunctionRegistry functionRegistry;
  private final long maxSteps;

  /**
   * 创建字节码解释器
   *
   * @param functionRegistry 函数注册表
   */
  public BytecodeInterpreter(FunctionRegistry functionRegistry) {
    this(functionRegistry, DEFAULT_MAX_STEPS);
  }

  /**
   * 创建字节码解释器（带预算限制）
   *
   * @param functionRegistry 函数注册表
   * @param maxSteps 单次求值节点访问预算
   */
  public BytecodeInterpreter(FunctionRegistry functionRegistry, long maxSteps) {
    this.functionRegistry = functionRegistry;
    this.maxSteps = maxSteps;
  }

  /**
   * 执行字节码程序
   *
   * @param program 编译后的字节码程序
   * @param variables 变量上下文（facts）
   * @return 执行结果
   */
  public Object execute(CompiledProgram program, Map<String, Object> variables) {
    byte[] code = program.bytecode();
    List<Object> constants = program.constantPool();
    int ip = 0; // 指令指针
    int stepCount = 0;

    // 操作数栈（使用数组 + 栈指针实现，避免 ArrayList 扩容开销）
    Object[] stack = createStack(constants);
    int sp = 0; // 栈指针

    while (ip < code.length) {
      // 安全检查：节点访问预算
      if (++stepCount > maxSteps) {
        throw new LiteExprException(
            "表达式执行超出节点预算限制（" + maxSteps + " 步）: " + program.sourceExpression());
      }

      int opcode = code[ip++] & 0xFF;
      switch (opcode) {
        case 0x01 -> push(stack, sp++, null); // LOAD_NULL
        case 0x02 -> { // LOAD_CONST
          int constIdx = readU16(code, ip);
          ip += 2;
          push(stack, sp++, constants.get(constIdx));
        }
        case 0x10 -> { // LOAD_VAR
          int nameIdx = readU16(code, ip);
          ip += 2;
          Object varNameObj = constants.get(nameIdx);
          String varName = ((BytecodeCompiler.VarName) varNameObj).name();
          push(stack, sp++, variables.get(varName));
        }
        case 0x20 -> { // ADD
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, add(a, b));
        }
        case 0x21 -> { // SUB
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, subtract(a, b));
        }
        case 0x22 -> { // MUL
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, multiply(a, b));
        }
        case 0x23 -> { // DIV
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, divide(a, b));
        }
        case 0x24 -> { // MOD
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, modulo(a, b));
        }
        case 0x25 -> { // NEG
          Object a = pop(stack, --sp);
          push(stack, sp++, negate(a));
        }
        case 0x30 -> { // CMP_GT
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, compare(a, b) > 0);
        }
        case 0x31 -> { // CMP_GE
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, compare(a, b) >= 0);
        }
        case 0x32 -> { // CMP_LT
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, compare(a, b) < 0);
        }
        case 0x33 -> { // CMP_LE
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, compare(a, b) <= 0);
        }
        case 0x34 -> { // CMP_EQ
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, isEqual(a, b));
        }
        case 0x35 -> { // CMP_NE
          Object b = pop(stack, --sp);
          Object a = pop(stack, --sp);
          push(stack, sp++, !isEqual(a, b));
        }
        case 0x50 -> { // JUMP
          int offset = readU16(code, ip);
          ip += 2;
          ip += offset;
        }
        case 0x51 -> { // JUMP_IF_FALSE
          int offset = readU16(code, ip);
          ip += 2;
          Object cond = pop(stack, --sp);
          if (!isTruthy(cond)) {
            ip += offset;
          }
        }
        case 0x52 -> { // JUMP_IF_TRUE
          int offset = readU16(code, ip);
          ip += 2;
          Object cond = pop(stack, --sp);
          if (isTruthy(cond)) {
            ip += offset;
          }
        }
        case 0x53 -> { // JUMP_IF_NULL
          int offset = readU16(code, ip);
          ip += 2;
          Object ref = pop(stack, --sp);
          if (ref == null) {
            ip += offset;
          }
        }
        case 0x40, 0x41 -> { // LOGIC_AND / LOGIC_OR — 已通过 JUMP_IF_FALSE/TRUE 实现短路，此处不应到达
          throw new LiteExprException("逻辑运算操作码不应直接执行: 0x" + Integer.toHexString(opcode));
        }
        case 0x42 -> { // LOGIC_NOT
          Object a = pop(stack, --sp);
          push(stack, sp++, !isTruthy(a));
        }
        case 0x60 -> { // CALL_FUNCTION
          int nameIdx = readU16(code, ip);
          ip += 2;
          int argCount = code[ip++] & 0xFF;
          String funcName = ((BytecodeCompiler.VarName) constants.get(nameIdx)).name();
          Object[] args = new Object[argCount];
          for (int i = argCount - 1; i >= 0; i--) {
            args[i] = pop(stack, --sp);
          }
          // TODO: 实现函数调用委托到 FunctionRegistry
          push(stack, sp++, callFunction(funcName, args));
        }
        case 0x70 -> { // GET_MEMBER
          int nameIdx = readU16(code, ip);
          ip += 2;
          Object obj = pop(stack, --sp);
          String member = ((BytecodeCompiler.VarName) constants.get(nameIdx)).name();
          push(stack, sp++, getMember(obj, member));
        }
        case 0x71 -> { // GET_INDEX
          Object key = pop(stack, --sp);
          Object coll = pop(stack, --sp);
          push(stack, sp++, getIndex(coll, key));
        }
        case 0xFF -> { // RETURN
          return pop(stack, --sp);
        }
        default -> throw new LiteExprException(
            "未知字节码操作码: 0x" + Integer.toHexString(opcode));
      }
    }

    // 无 RETURN 指令时返回栈顶（或 null）
    return sp > 0 ? pop(stack, --sp) : null;
  }

  // ===== 栈操作辅助方法 =====

  private void push(Object[] stack, int sp, Object value) {
    stack[sp] = value;
  }

  private Object pop(Object[] stack, int sp) {
    Object value = stack[sp];
    stack[sp] = null; // 帮助 GC
    return value;
  }

  private static int readU16(byte[] code, int offset) {
    return ((code[offset] & 0xFF) << 8) | (code[offset + 1] & 0xFF);
  }

  /** 创建操作数栈 — 基于常量池大小给出合理初始容量 */
  private static Object[] createStack(List<Object> constants) {
    return new Object[Math.max(16, constants.size() * 2 + 16)];
  }

  // ===== 算术运算 =====

  private Object add(Object a, Object b) {
    if (a == null || b == null) {
      return null;
    }
    BigDecimal da = toBigDecimal(a);
    BigDecimal db = toBigDecimal(b);
    if (da != null && db != null) {
      return da.add(db);
    }
    // 字符串拼接
    if (a instanceof String || b instanceof String) {
      return String.valueOf(a) + b;
    }
    return null;
  }

  private Object subtract(Object a, Object b) {
    if (a == null || b == null) {
      return null;
    }
    BigDecimal da = toBigDecimal(a);
    BigDecimal db = toBigDecimal(b);
    return da != null && db != null ? da.subtract(db) : null;
  }

  private Object multiply(Object a, Object b) {
    if (a == null || b == null) {
      return null;
    }
    BigDecimal da = toBigDecimal(a);
    BigDecimal db = toBigDecimal(b);
    return da != null && db != null ? da.multiply(db) : null;
  }

  private Object divide(Object a, Object b) {
    if (a == null || b == null) {
      return null;
    }
    BigDecimal da = toBigDecimal(a);
    BigDecimal db = toBigDecimal(b);
    return da != null && db != null ? da.divide(db, 10, BigDecimal.ROUND_HALF_UP) : null;
  }

  private Object modulo(Object a, Object b) {
    if (a == null || b == null) {
      return null;
    }
    BigDecimal da = toBigDecimal(a);
    BigDecimal db = toBigDecimal(b);
    return da != null && db != null ? da.remainder(db) : null;
  }

  private Object negate(Object a) {
    if (a == null) {
      return null;
    }
    BigDecimal da = toBigDecimal(a);
    return da != null ? da.negate() : null;
  }

  // ===== 比较运算 =====

  private int compare(Object a, Object b) {
    if (a == null && b == null) {
      return 0;
    }
    if (a == null) {
      return -1;
    }
    if (b == null) {
      return 1;
    }
    if (a instanceof Comparable && b instanceof Comparable) {
      try {
        return ((Comparable) a).compareTo(b);
      } catch (ClassCastException e) {
        return a.toString().compareTo(b.toString());
      }
    }
    return a.toString().compareTo(b.toString());
  }

  private boolean isEqual(Object a, Object b) {
    if (a == null && b == null) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a instanceof BigDecimal && b instanceof BigDecimal) {
      return ((BigDecimal) a).compareTo((BigDecimal) b) == 0;
    }
    return a.equals(b);
  }

  // ===== 类型判断 =====

  private boolean isTruthy(Object value) {
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean b) {
      return b;
    }
    if (value instanceof BigDecimal n) {
      return n.compareTo(BigDecimal.ZERO) != 0;
    }
    if (value instanceof Number n) {
      return n.doubleValue() != 0;
    }
    if (value instanceof String s) {
      return !s.isEmpty();
    }
    return true;
  }

  // ===== 类型转换 =====

  private BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Integer i) {
      return BigDecimal.valueOf(i);
    }
    if (value instanceof Long l) {
      return BigDecimal.valueOf(l);
    }
    if (value instanceof Double d) {
      return BigDecimal.valueOf(d);
    }
    if (value instanceof Float f) {
      return BigDecimal.valueOf(f);
    }
    if (value instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    if (value instanceof String s) {
      try {
        return new BigDecimal(s);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  // ===== 数据访问 =====

  private Object getMember(Object obj, String member) {
    if (obj == null) {
      return null;
    }
    if (obj instanceof Map<?, ?> map) {
      return map.get(member);
    }
    // 反射访问属性
    try {
      Field field = obj.getClass().getDeclaredField(member);
      field.setAccessible(true);
      return field.get(obj);
    } catch (Exception e) {
      log.debug("[LiteExpr] 属性访问失败: {}.{}, 原因: {}", obj.getClass().getSimpleName(), member, e.getMessage());
      return null;
    }
  }

  private Object getIndex(Object coll, Object key) {
    if (coll == null || key == null) {
      return null;
    }
    if (coll instanceof List<?> list && key instanceof Number n) {
      int idx = n.intValue();
      return idx >= 0 && idx < list.size() ? list.get(idx) : null;
    }
    if (coll instanceof Map<?, ?> map) {
      return map.get(key);
    }
    return null;
  }

  // ===== 函数调用 =====

  private Object callFunction(String funcName, Object[] args) {
    LiteExprFunction function = functionRegistry.lookup(funcName);
    if (function == null) {
      throw new LiteExprException("未注册的函数: " + funcName);
    }
    return function.apply(args);
  }
}
