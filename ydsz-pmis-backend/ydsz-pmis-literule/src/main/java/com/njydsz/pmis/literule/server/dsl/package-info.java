/**
 * 规则引擎 - 领域特定语言（DSL）层。
 *
 * <p>literule 自研的"业务人员可读"DSL 解析器，将文本规则转换为内部 {@code Rule} 抽象：
 * <ul>
 *   <li>{@code RuleDslLexer}   - DSL 词法分析</li>
 *   <li>{@code RuleDslParser}   - DSL 语法分析（ANTLR / 手写）</li>
 *   <li>{@code RuleDslBuilder}  - DSL 构造器（编程式）</li>
 *   <li>{@code RuleSerializer}  - 规则序列化（JSON / YAML）</li>
 * </ul>
 *
 * <h3>DSL 示例</h3>
 * <pre>
 *   RULE "合同金额上限校验"
 *     WHEN
 *       contract.amount > 1000000
 *     THEN
 *       requireApprovalLevel = 2
 *     PRIORITY 100
 *   END
 * </pre>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>DSL 语法与"自然语言中文"尽量接近</li>
 *   <li>语法错误必须给出可读的位置提示</li>
 *   <li>支持 IDE 自动补全（VSCode 插件 / JetBrains 插件）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.server.dsl;
