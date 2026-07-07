/**
 * 规则引擎 - 表达式引擎层。
 *
 * <p>基于 Aviator 5.x 增强的表达式引擎，支持：
 * <ul>
 *   <li>数学运算（+ / - / * / / / %）</li>
 *   <li>比较运算（== / != / &gt; / &lt; / &gt;= / &lt;=）</li>
 *   <li>逻辑运算（&amp;&amp; / || / !）</li>
 *   <li>三元运算（condition ? a : b）</li>
 *   <li>函数调用（自定义函数注册）</li>
 *   <li>变量引用（来自 {@code RuleContext}）</li>
 *   <li>字符串 / 日期 / 集合操作</li>
 * </ul>
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code ExpressionEngine}    - 表达式引擎门面</li>
 *   <li>{@code ExpressionCompileCache} - 表达式编译缓存</li>
 *   <li>{@code ExpressionContext}    - 表达式上下文</li>
 *   <li>{@code ExpressionValidator}  - 表达式语法校验</li>
 *   <li>{@code CustomFunctionLoader} - 自定义函数加载器（SPI）</li>
 * </ul>
 *
 * <h3>使用约束</h3>
 * <ul>
 *   <li>所有自定义函数必须是无状态的（线程安全）</li>
 *   <li>表达式执行时间复杂度应可控（避免长循环）</li>
 *   <li>表达式支持热更新（无需重启服务）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.expr;
