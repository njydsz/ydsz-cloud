package com.njydsz.literule.server.engine.liteexpr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.custom.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BytecodeInterpreter} 安全边界测试。
 *
 * <p>规则引擎在租户自定义表达式场景下执行不可信输入，解释器的资源上限
 * （节点预算 / 墙上时钟超时 / 未知操作码）是防止 ReDoS 与死循环的最后防线，
 * 此前完全没有测试覆盖。
 *
 * <p>测试直接构造 {@link CompiledProgram}（字节码 + 常量池），
 * 不依赖解析器，聚焦解释器自身的安全语义。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("BytecodeInterpreter — 资源上限与安全边界")
class BytecodeInterpreterSafetyTest {

  private static final String SOURCE = "test-expression";

  /** 用 LOAD_NULL 作为「可安全重复执行」的填充指令（每条 1 字节，只压栈不依赖栈状态）。 */
  private static byte[] fillWithLoadNull(int count) {
    byte[] code = new byte[count];
    for (int i = 0; i < count; i++) {
      code[i] = (byte) BytecodeOpcode.LOAD_NULL.code();
    }
    return code;
  }

  @Nested
  @DisplayName("节点预算（maxSteps）")
  class StepBudget {

    @Test
    @DisplayName("执行步数超过预算时抛出 BusinessException（节点预算保护）")
    void throwsWhenStepBudgetExceeded() {
      BytecodeInterpreter interpreter = new BytecodeInterpreter(new FunctionRegistry(), 10L);
      // 编译产物只生成前向跳转，因此用「超长直序程序 + 极小预算」触发步数上限：
      // 每条 LOAD_NULL 占 1 字节，64 条指令必然超过 10 步预算。
      byte[] code = fillWithLoadNull(64);
      CompiledProgram program = new CompiledProgram(code, List.of(), SOURCE);

      assertThatThrownBy(() -> interpreter.execute(program, new HashMap<>()))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("节点预算");
    }

    @Test
    @DisplayName("预算充足时正常执行完成")
    void completesWhenBudgetSufficient() {
      BytecodeInterpreter interpreter = new BytecodeInterpreter(new FunctionRegistry(), 100L);
      byte[] code = {(byte) BytecodeOpcode.LOAD_NULL.code(),
                     (byte) BytecodeOpcode.RETURN.code()};
      CompiledProgram program = new CompiledProgram(code, List.of(), SOURCE);

      assertThat(interpreter.execute(program, new HashMap<>())).isNull();
    }
  }

  @Nested
  @DisplayName("未知操作码")
  class UnknownOpcode {

    @Test
    @DisplayName("非法操作码抛出 LiteExprException 而非数组越界")
    void throwsForIllegalOpcode() {
      BytecodeInterpreter interpreter = new BytecodeInterpreter(new FunctionRegistry(), 100L);
      // 0xFE 不在任何已定义操作码中（RETURN=0xFF，其余均小于 0xF0）；
      // BytecodeOpcode.fromCode 抛 BusinessException（LiteExprException 的父类）。
      byte[] code = {(byte) 0xFE};
      CompiledProgram program = new CompiledProgram(code, List.of(), SOURCE);

      assertThatThrownBy(() -> interpreter.execute(program, new HashMap<>()))
          .isInstanceOf(BusinessException.class);
    }
  }

  @Nested
  @DisplayName("墙上时钟超时")
  class WallClockTimeout {

    @Test
    @DisplayName("超时时间设为 0 表示不限时（向后兼容）")
    void zeroTimeoutMeansUnlimited() {
      BytecodeInterpreter interpreter = new BytecodeInterpreter(
          new FunctionRegistry(), 1000L, 0L);
      // 常量池非空以保证栈容量充足，验证「不限时」语义本身
      byte[] code = {(byte) BytecodeOpcode.LOAD_NULL.code(),
                     (byte) BytecodeOpcode.RETURN.code()};
      CompiledProgram program =
          new CompiledProgram(code, List.of("pad"), SOURCE);

      assertThat(interpreter.execute(program, new HashMap<>())).isNull();
    }

    @Test
    @DisplayName("操作数栈溢出时抛出业务异常而非数组越界")
    void throwsOnStackOverflow() {
      BytecodeInterpreter interpreter = new BytecodeInterpreter(
          new FunctionRegistry(), 1000L, 0L);
      // 常量池为空 → 栈容量取最小值；连续压栈必然溢出
      byte[] code = fillWithLoadNull(64);
      CompiledProgram program = new CompiledProgram(code, List.of(), SOURCE);

      assertThatThrownBy(() -> interpreter.execute(program, new HashMap<>()))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("栈溢出");
    }

    @Test
    @DisplayName("操作数栈下溢时抛出业务异常而非数组越界")
    void throwsOnStackUnderflow() {
      BytecodeInterpreter interpreter = new BytecodeInterpreter(
          new FunctionRegistry(), 1000L, 0L);
      // POP 在空栈上执行 → 下溢
      byte[] code = {(byte) BytecodeOpcode.POP.code()};
      CompiledProgram program = new CompiledProgram(code, List.of(), SOURCE);

      assertThatThrownBy(() -> interpreter.execute(program, new HashMap<>()))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("栈下溢");
    }

    @Test
    @DisplayName("负超时同样视为不限时，不抛异常")
    void negativeTimeoutMeansUnlimited() {
      BytecodeInterpreter interpreter = new BytecodeInterpreter(
          new FunctionRegistry(), 1000L, -1L);
      byte[] code = {(byte) BytecodeOpcode.LOAD_NULL.code(),
                     (byte) BytecodeOpcode.RETURN.code()};
      CompiledProgram program = new CompiledProgram(code, List.of(), SOURCE);

      assertThat(interpreter.execute(program, new HashMap<>())).isNull();
    }
  }

  @Nested
  @DisplayName("基础执行语义")
  class BasicExecution {

    @Test
    @DisplayName("LOAD_NULL + RETURN 返回 null")
    void loadNullThenReturn() {
      BytecodeInterpreter interpreter = new BytecodeInterpreter(new FunctionRegistry(), 100L);
      byte[] code = {(byte) BytecodeOpcode.LOAD_NULL.code(),
                     (byte) BytecodeOpcode.RETURN.code()};
      CompiledProgram program = new CompiledProgram(code, List.of(), SOURCE);

      assertThat(interpreter.execute(program, new HashMap<>())).isNull();
    }

    @Test
    @DisplayName("LOAD_CONST 从常量池加载字面量")
    void loadConstFromPool() {
      BytecodeInterpreter interpreter = new BytecodeInterpreter(new FunctionRegistry(), 100L);
      // LOAD_CONST 的操作数是 u16 常量池索引（大端）：索引 0
      byte[] code = {
          (byte) BytecodeOpcode.LOAD_CONST.code(),
          0x00, 0x00,
          (byte) BytecodeOpcode.RETURN.code(),
      };
      CompiledProgram program =
          new CompiledProgram(code, List.of("hello-literal"), SOURCE);

      assertThat(interpreter.execute(program, new HashMap<>())).isEqualTo("hello-literal");
    }
  }
}
