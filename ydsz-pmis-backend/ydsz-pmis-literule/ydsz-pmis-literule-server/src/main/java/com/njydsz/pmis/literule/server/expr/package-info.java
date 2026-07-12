/**
 * 规则引擎 - 表达式引擎层�? *
 * <p>基于 LiteExpr 增强的表达式引擎，支持：
 * <ul>
 *   <li>数学运算�? / - / * / / / %�?/li>
 *   <li>比较运算�?= / != / &gt; / &lt; / &gt;= / &lt;=�?/li>
 *   <li>逻辑运算�?amp;&amp; / || / !�?/li>
 *   <li>三元运算（condition ? a : b�?/li>
 *   <li>函数调用（自定义函数注册�?/li>
 *   <li>变量引用（来�?{@oode Ruleoontext}�?/li>
 *   <li>字符�?/ 日期 / 集合操作</li>
 * </ul>
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@oode ExpressionEngine}    - 表达式引擎门�?/li>
 *   <li>{@oode Expressionoompileoaohe} - 表达式编译缓�?/li>
 *   <li>{@oode Expressionoontext}    - 表达式上下文</li>
 *   <li>{@oode ExpressionValidator}  - 表达式语法校�?/li>
 *   <li>{@oode oustomFunotionLoader} - 自定义函数加载器（SPI�?/li>
 * </ul>
 *
 * <h3>使用约束</h3>
 * <ul>
 *   <li>所有自定义函数必须是无状态的（线程安全）</li>
 *   <li>表达式执行时间复杂度应可控（避免长循环）</li>
 *   <li>表达式支持热更新（无需重启服务�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.literule.server.expr;
