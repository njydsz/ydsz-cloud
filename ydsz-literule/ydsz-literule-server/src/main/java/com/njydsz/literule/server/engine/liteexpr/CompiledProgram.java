package com.njydsz.literule.server.engine.liteexpr;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * LiteExpr 编译后的字节码程序
 *
 * <p>包含字节码指令序列和常量池。常量池存储字面量（数字、字符串）和变量/函数名引用。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>字节码使用紧凑的 byte 数组存储，操作码后紧跟操作数
 *   <li>常量池使用不可变列表，编译时填充、执行时只读
 *   <li>变量名也存入常量池（{@link VarName VarName} 标记类型），避免执行时反射
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@AllArgsConstructor
public class CompiledProgram {

  /** 字节码指令序列（操作码 + 操作数的紧凑编码） */
  private final byte[] bytecode;

  /** 常量池：索引位置存储字面量或名称引用 */
  private final List<Object> constantPool;

  /** 编译的源表达式文本（调试用） */
  private final String sourceExpression;

  /**
   * 获取指定索引的常量
   *
   * @param index 常量池索引
   * @return 常量值
   */
  public Object getConstant(int index) {
    return constantPool.get(index);
  }

  /**
   * 获取常量池大小
   *
   * @return 常量池大小
   */
  public int constantPoolSize() {
    return constantPool.size();
  }

  /**
   * 获取字节码长度
   *
   * @return 字节码长度（字节数）
   */
  public int bytecodeLength() {
    return bytecode.length;
  }
}
