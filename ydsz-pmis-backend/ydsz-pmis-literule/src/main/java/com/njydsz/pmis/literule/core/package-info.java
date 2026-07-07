/**
 * 规则引擎 - 核心层。
 *
 * <p>规则引擎的"心脏"：定义规则 / 规则集 / 上下文 / 执行器。
 *
 * <h3>核心抽象</h3>
 * <ul>
 *   <li>{@code Rule}        - 规则接口（单条规则）</li>
 *   <li>{@code RuleSet}     - 规则集（一组规则）</li>
 *   <li>{@code RuleContext} - 规则执行上下文（输入参数 / 共享变量）</li>
 *   <li>{@code RuleEngine}  - 规则执行引擎接口</li>
 *   <li>{@code DefaultRuleEngine} - 默认实现（顺序 / 并行 / 短路）</li>
 *   <li>{@code RuleHit}     - 规则命中结果</li>
 *   <li>{@code RuleResult}  - 规则执行结果</li>
 *   <li>{@code RuleVersion} - 规则版本（按版本灰度）</li>
 * </ul>
 *
 * <h3>执行模式</h3>
 * <ul>
 *   <li>{@code SEQUENCE} - 顺序执行（命中即短路）</li>
 *   <li>{@code ALL}      - 执行所有规则（取最后一个命中）</li>
 *   <li>{@code SCORE}    - 评分制（按权重汇总）</li>
 *   <li>{@code PRIORITY} - 优先级制（按优先级执行）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>规则无副作用（不能修改输入参数）</li>
 *   <li>规则执行支持超时（默认 5s）</li>
 *   <li>规则失败不影响后续规则执行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.core;
