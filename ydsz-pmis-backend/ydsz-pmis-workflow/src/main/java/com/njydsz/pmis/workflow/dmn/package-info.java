/**
 * DMN 决策表引擎。
 *
 * <p>对标 Camunda / Flowable DMN 规范，提供规则驱动的决策能力。
 * 在流程节点（如排他网关、服务任务）执行前后，将业务变量传入决策表，
 * 由命中策略（HIT_POLICY）输出决策结果，参与后续节点路由、审批人指定、自动分支等场景。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.workflow.dmn.DmnDecisionTable} - 决策表定义（输入 / 输出 / 规则行）</li>
 *   <li>{@link com.njydsz.pmis.workflow.dmn.DmnInput} - 输入列定义（变量名、类型、表达式）</li>
 *   <li>{@link com.njydsz.pmis.workflow.dmn.DmnOutput} - 输出列定义（变量名、类型、默认值）</li>
 *   <li>{@link com.njydsz.pmis.workflow.dmn.DmnRule} - 规则行（输入条件 + 输出值）</li>
 *   <li>{@link com.njydsz.pmis.workflow.dmn.DmnHitPolicy} - 命中策略（FIRST / UNIQUE / PRIORITY / ANY / COLLECT / RULE_ORDER）</li>
 *   <li>{@link com.njydsz.pmis.workflow.dmn.DmnEngine} - 决策表执行器（遍历规则、匹配条件、聚合输出）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>不引入 SpEL 等重表达式引擎，条件匹配使用简单解析（{@code >} / {@code <} / {@code ==} /
 *       {@code in()} / {@code between()} / {@code contains()}），运维可直接在设计器中编写。</li>
 *   <li>输入 / 输出类型与 Java 强类型映射，避免装箱 / 拆箱造成的精度丢失。</li>
 *   <li>命中策略严格遵循 DMN 1.3 规范：FIRST 取首条匹配、UNIQUE 必须唯一、PRIORITY 按输出优先级、
 *       ANY 任一匹配即可、COLLECT 聚合所有匹配、RULE_ORDER 按规则顺序聚合。</li>
 *   <li>本包为引擎内置能力，决策表元数据持久化在 {@code pmis_flow_dmn_table}（参见
 *       {@code com.njydsz.pmis.workflow.entity.FlowDmnTableDO}）。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.dmn;
