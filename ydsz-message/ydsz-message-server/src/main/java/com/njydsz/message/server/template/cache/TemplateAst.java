package com.njydsz.message.server.template.cache;

import java.util.List;

import lombok.Getter;

/**
 * 编译后的模板 AST（抽象语法树）。
 *
 * <p>将模板字符串预编译为指令列表，避免每次渲染都重复正则匹配。
 * 指令类型包括：
 * <ul>
 *   <li><b>TEXT</b>：静态文本片段</li>
 *   <li><b>VAR</b>：变量占位符 {@code ${var}}</li>
 *   <li><b>IF</b>：条件块 {@code {{#if var}}...{{else}}...{{/if}}</li>
 *   <li><b>EACH</b>：循环块 {@code {{#each list}}...{{/each}}</li>
 * </ul>
 *
 * <p>使用 {@code CachedTemplateEngine} 将高频模板编译为 AST 并缓存，
 * 渲染时直接遍历指令列表，时间复杂度从 O(正则匹配次数) 降为 O(指令数)。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public class TemplateAst {

  /** 编译时间戳（用于缓存淘汰判断） */
  private final long compileTimestamp;

  /** 原始模板字符串 */
  private final String originalTemplate;

  /** 指令列表 */
  private final List<AstInstruction> instructions;

  public TemplateAst(String originalTemplate, List<AstInstruction> instructions) {
    this.originalTemplate = originalTemplate;
    this.instructions = instructions;
    this.compileTimestamp = System.currentTimeMillis();
  }

  /** AST 指令。 */
  @Getter
  public static class AstInstruction {

    /** 指令类型 */
    private final InstructionType type;

    /** 静态文本（type=TEXT 时） */
    private final String text;

    /** 变量表达式（type=VAR 时）：变量名或管道表达式 */
    private final String expression;

    /** 条件 key（type=IF 时） */
    private final String conditionKey;

    /** if 分支体（type=IF 时） */
    private final TemplateAst trueBranch;

    /** else 分支体（type=IF 时，可为 null） */
    private final TemplateAst falseBranch;

    /** 循环列表 key（type=EACH 时） */
    private final String iterationKey;

    /** 循环体（type=EACH 时） */
    private final TemplateAst body;

    private AstInstruction(
        InstructionType type,
        String text,
        String expression,
        String conditionKey,
        TemplateAst trueBranch,
        TemplateAst falseBranch,
        String iterationKey,
        TemplateAst body) {
      this.type = type;
      this.text = text;
      this.expression = expression;
      this.conditionKey = conditionKey;
      this.trueBranch = trueBranch;
      this.falseBranch = falseBranch;
      this.iterationKey = iterationKey;
      this.body = body;
    }

    /**
     * 静态文本指令
     *
     * @param text 静态文本内容
     * @return 静态文本指令实例
     */
    static AstInstruction text(String text) {
      return new AstInstruction(InstructionType.TEXT, text, null, null, null, null, null, null);
    }

    /**
     * 变量指令
     *
     * @param expression 变量表达式（变量名或管道表达式）
     * @return 变量指令实例
     */
    static AstInstruction var(String expression) {
      return new AstInstruction(
          InstructionType.VAR, null, expression, null, null, null, null, null);
    }

    /**
     * if 条件指令
     *
     * @param conditionKey 条件判断键
     * @param trueBranch 条件为真时的分支 AST
     * @param falseBranch 条件为假时的分支 AST（可为 null）
     * @return 条件指令实例
     */
    static AstInstruction ifBlock(
        String conditionKey, TemplateAst trueBranch, TemplateAst falseBranch) {
      return new AstInstruction(
          InstructionType.IF, null, null, conditionKey, trueBranch, falseBranch, null, null);
    }

    /**
     * each 循环指令
     *
     * @param iterationKey 循环迭代变量键
     * @param body 循环体 AST
     * @return 循环指令实例
     */
    static AstInstruction eachBlock(String iterationKey, TemplateAst body) {
      return new AstInstruction(
          InstructionType.EACH, null, null, null, null, null, iterationKey, body);
    }
  }

  /** 指令类型枚举。 */
  public enum InstructionType {
    /** 静态文本 */
    TEXT,
    /** 变量替换 */
    VAR,
    /** 条件渲染 */
    IF,
    /** 循环渲染 */
    EACH
  }
}
