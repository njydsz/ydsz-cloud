/**
 * 统一 DAG 内核模块（P0-1 架构优化）。
 *
 * <p>抽取 cronjob 和 agent 两个模块中重复的 DAG 基础设施，
 * 统一到 common 模块供所有业务模块复用：
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.dag.DagNodeStatus} — 统一节点状态枚举</li>
 *   <li>{@link com.njydsz.pmis.common.dag.DagInstanceStatus} — 统一实例状态枚举</li>
 *   <li>{@link com.njydsz.pmis.common.dag.DagFailureStrategy} — 统一失败策略枚举</li>
 *   <li>{@link com.njydsz.pmis.common.dag.DagGraph} — 统一拓扑分析工具（Kahn 拓扑排序、分层排序、环检测、上下游闭包）</li>
 *   <li>{@link com.njydsz.pmis.common.dag.SpELConditionEvaluator} — 统一 SpEL 条件表达式引擎</li>
 * </ul>
 *
 * <p>各业务模块（cronjob / agent）保留各自的执行引擎和持久化层，
 * 仅复用本包中的纯算法工具和状态枚举。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-1)
 */
package com.njydsz.pmis.common.dag;
