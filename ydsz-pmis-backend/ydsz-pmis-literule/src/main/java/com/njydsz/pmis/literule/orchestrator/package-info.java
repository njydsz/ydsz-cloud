/**
 * 规则引擎 - 多规则编排层。
 *
 * <p>支持"多个规则集协同工作"的场景，提供：
 * <ul>
 *   <li>顺序编排：上一个规则集输出作为下一个输入</li>
 *   <li>并行编排：多个规则集同时跑，结果聚合</li>
 *   <li>分支编排：根据路由选择不同规则集</li>
 *   <li>嵌套编排：规则集中嵌套子规则集</li>
 * </ul>
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code RuleOrchestrator}     - 规则编排器</li>
 *   <li>{@code OrchestrationPlan}     - 编排计划（DAG 描述）</li>
 *   <li>{@code OrchestrationResult}   - 编排结果</li>
 *   <li>{@code RoutingStrategy}       - 路由策略（按上下文路由到不同规则集）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>编排计划可持久化（按版本管理）</li>
 *   <li>编排执行可观测（每个节点的执行细节）</li>
 *   <li>编排失败支持部分回滚 / 重试</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.orchestrator;
