package com.njydsz.pmis.common.dag;

import java.util.Map;

/**
 * 统一 DAG 执行器接口（P0-3 架构优化）。
 *
 * <p>提供通用的 DAG 执行抽象，适用于 cronjob（任务调度 DAG）和 agent（AI 编排 DAG）等不同场景。
 * 各模块实现此接口，保持执行入口统一。
 *
 * <h3>实现方</h3>
 * <ul>
 *   <li>{@code com.njydsz.pmis.cronjob.core.dag.DagExecutor} — 任务调度 DAG 执行器</li>
 *   <li>{@code com.njydsz.pmis.agent.orchestration.dag.DagExecutor} — AI 编排 DAG 执行器</li>
 * </ul>
 *
 * @param <D> DAG 定义类型（模块特有）
 * @param <R> 执行结果类型（模块特有）
 * @author ydsz-pmis-team
 * @since 1.6.0 (P0-3)
 */
public interface DagExecutor<D, R> {

    /**
     * 执行 DAG。
     *
     * <p>根据 DAG 定义，按拓扑顺序执行各节点，返回执行结果。
     * 实现方应支持：
     * <ul>
     *   <li>分层并行执行（同一拓扑层的节点无依赖关系，可并行）</li>
     *   <li>条件分支（节点可配置条件表达式，求值为 false 时跳过）</li>
     *   <li>失败策略（ABORT / CONTINUE / RETRY / SKIP_SUBSEQUENT）</li>
     *   <li>超时控制（节点级超时）</li>
     *   <li>上下文传递（上游节点输出注入下游节点）</li>
     * </ul>
     *
     * @param definition DAG 定义
     * @param inputs     输入参数（可为 null）
     * @return 执行结果
     */
    R execute(D definition, Map<String, Object> inputs);

    /**
     * 校验 DAG 定义合法性（拓扑结构、环检测等）。
     *
     * @param definition DAG 定义
     * @return true 表示合法
     */
    boolean validate(D definition);
}
